package aes

import chisel3._
import chisel3.util._

// Implement wrapper for unrolled AES cipher and inverse cipher
// Change Nk=4 for AES128, NK=6 for AES192, Nk=8 for AES256
// Change expandedKeyMemType= ROM, Mem, SyncReadMem
// Change unrolled=[1..Nrplus1] for unroll depth
class UnrolledAES(Nk: Int, unrolled: Int, SubBytes_SCD: Boolean, InvSubBytes_SCD: Boolean, expandedKeyMemType: String) extends Module {
  require(Nk == 4 || Nk == 6 || Nk == 8)
  require(expandedKeyMemType == "ROM" || expandedKeyMemType == "Mem" || expandedKeyMemType == "SyncReadMem")
  val KeyLength: Int = Nk * Params.rows
  val Nr: Int = Nk + 6 // 10, 12, 14 rounds
  val Nrplus1: Int = Nr + 1 // 10+1, 12+1, 14+1
  val EKDepth: Int = 16 // enough memory for any expanded key
  require((unrolled > 0) && (unrolled < Nrplus1))

  val io = IO(new Bundle {
    val AES_mode = Input(UInt(2.W)) //  0=00=off, 1=01=expanded key update, 2=10=cipher, 3=11=inverse cipher
    val input_text = Input(Vec(Params.StateLength, UInt(8.W))) // plaintext, ciphertext, roundKey
    val output_text = Output(Vec(Params.StateLength, UInt(8.W))) // ciphertext or plaintext
    val output_valid = Output(Bool())
  })

  // Declare instances and array of Cipher and Inverse Cipher Rounds
  val CipherRoundARK = CipherRound("AddRoundKeyOnly", SubBytes_SCD)
  val CipherRounds = Array.fill(Nr - 1) {
    Module(new CipherRound("CompleteRound", SubBytes_SCD)).io
  }
  val CipherRoundNMC = CipherRound("NoMixColumns", SubBytes_SCD)

  val InvCipherRoundARK = InvCipherRound("AddRoundKeyOnly", InvSubBytes_SCD)
  val InvCipherRounds = Array.fill(Nr - 1) {
    Module(new InvCipherRound("CompleteRound", InvSubBytes_SCD)).io
  }
  val InvCipherRoundNMC = InvCipherRound("NoInvMixColumns", InvSubBytes_SCD)

  // A roundKey is Params.StateLength bytes, and 1+(10/12/14) (< EKDepth) of them are needed
  // Mem = combinational/asynchronous-read, sequential/synchronous-write = register banks
  // Create a asynchronous-read, synchronous-write memory block big enough for any key length
  // val expandedKeyARMem = Mem(EKDepth, Vec(Params.StateLength, UInt(8.W)))

  // SyncReadMem = sequential/synchronous-read, sequential/synchronous-write = SRAMs
  // Create a synchronous-read, synchronous-write memory block big enough for any key length
  // val expandedKeySRMem = SyncReadMem(EKDepth, Vec(Params.StateLength, UInt(8.W)))

  // use the same address and dataOut val elements to interface with the parameterized memory
  val address = RegInit(0.U(log2Ceil(EKDepth).W))
  // val dataOut = Wire(Vec(Params.StateLength, UInt(8.W)))

  val expandedKeys = Reg(Vec(16, Vec(Params.StateLength, UInt(8.W))))

  when(io.AES_mode === 1.U) { // write to memory
    // if (expandedKeyMemType == "Mem") {
    //   expandedKeyARMem.write(address, io.input_text)
    // }
    // else if (expandedKeyMemType == "SyncReadMem") {
    //   expandedKeySRMem.write(address, io.input_text)
    // }
    // dataOut := DontCare
    address := address + 1.U
    expandedKeys(address) := io.input_text
  }
  //    .otherwise { // read from memory
  //      if (expandedKeyMemType == "Mem") {
  //        dataOut := expandedKeyARMem.read(address)
  //      }
  //      else if (expandedKeyMemType == "SyncReadMem") {
  //        dataOut := expandedKeySRMem.read(address)
  //      }
  //      else if (expandedKeyMemType == "ROM") {
  //        Nk match {
  //          case 4 => dataOut := ROMeKeys.expandedKey128(address)
  //          case 6 => dataOut := ROMeKeys.expandedKey192(address)
  //          case 8 => dataOut := ROMeKeys.expandedKey256(address)
  //        }
  //      }
  //
  //      // address logistics
  //      when(
  //        if ((expandedKeyMemType == "Mem") || (expandedKeyMemType == "ROM")) {
  //          (ShiftRegister(io.AES_mode, 1) === 2.U) // delay by 1 for Mem and ROM
  //        }
  //        else {
  //          (io.AES_mode === 2.U) // no delay for SyncReadMem
  //        }
  //      ) {
  //        address := address + 1.U
  //      }
  //        .elsewhen(io.AES_mode === 3.U) {
  //          when(address === 0.U) {
  //            address := Nr.U
  //          }
  //            .otherwise {
  //              address := address - 1.U
  //            }
  //        }
  //        .otherwise {
  //          address := 0.U
  //        }
  //    }

  // Cipher ARK round
  when(io.AES_mode === 2.U) { // cipher mode
    CipherRoundARK.io.input_valid := true.B
    CipherRoundARK.io.state_in := io.input_text
    CipherRoundARK.io.roundKey := expandedKeys(0)
  }.otherwise {
    CipherRoundARK.io.input_valid := false.B
    CipherRoundARK.io.state_in := DontCare
    CipherRoundARK.io.roundKey := DontCare
  }

  // Cipher Nr-1 rounds
  for (i <- 0 until (Nr - 1)) yield {
    if (i == 0) {
      CipherRounds(i).input_valid := CipherRoundARK.io.output_valid
      CipherRounds(i).state_in := CipherRoundARK.io.state_out
    }
    else {
      CipherRounds(i).input_valid := CipherRounds(i - 1).output_valid
      CipherRounds(i).state_in := CipherRounds(i - 1).state_out
    }
    CipherRounds(i).roundKey := expandedKeys(i + 1)
  }

  // Cipher last round
  CipherRoundNMC.io.input_valid := CipherRounds(Nr - 1 - 1).output_valid
  CipherRoundNMC.io.state_in := CipherRounds(Nr - 1 - 1).state_out
  CipherRoundNMC.io.roundKey := expandedKeys(Nr)

  //// Wire Inverse Cipher modules together

  // InvCipher ARK round
  when(io.AES_mode === 3.U) { // cipher mode
    InvCipherRoundARK.io.input_valid := true.B
    InvCipherRoundARK.io.state_in := io.input_text
    InvCipherRoundARK.io.roundKey := expandedKeys(Nr)
  }.otherwise {
    InvCipherRoundARK.io.input_valid := false.B
    InvCipherRoundARK.io.state_in := DontCare
    InvCipherRoundARK.io.roundKey := DontCare
  }

  // Cipher Nr-1 rounds
  for (i <- 0 until (Nr - 1)) yield {
    if (i == 0) {
      InvCipherRounds(i).input_valid := InvCipherRoundARK.io.output_valid
      InvCipherRounds(i).state_in := InvCipherRoundARK.io.state_out
    }
    else {
      InvCipherRounds(i).input_valid := InvCipherRounds(i - 1).output_valid
      InvCipherRounds(i).state_in := InvCipherRounds(i - 1).state_out
    }
    InvCipherRounds(i).roundKey := expandedKeys(Nr - i - 1)
  }

  // Cipher last round
  InvCipherRoundNMC.io.input_valid := InvCipherRounds(Nr - 1 - 1).output_valid
  InvCipherRoundNMC.io.state_in := InvCipherRounds(Nr - 1 - 1).state_out
  InvCipherRoundNMC.io.roundKey := expandedKeys(0)

  //  io.output_text := CipherRoundNMC.io.state_out //Mux((io.AES_mode === 2.U), InvCipherRoundNMC.io.state_out, CipherRoundNMC.io.state_out)
  io.output_text := Mux(CipherRoundNMC.io.output_valid, CipherRoundNMC.io.state_out, InvCipherRoundNMC.io.state_out)
  io.output_valid := CipherRoundNMC.io.output_valid || InvCipherRoundNMC.io.output_valid

}

object UnrolledAES {
  def apply(Nk: Int, unrolled: Int, SubBytes_SCD: Boolean, InvSubBytes_SCD: Boolean, expandedKeyMemType: String): UnrolledAES = Module(new UnrolledAES(Nk, unrolled, SubBytes_SCD, InvSubBytes_SCD, expandedKeyMemType))
}