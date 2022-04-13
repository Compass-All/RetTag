package lfsr

import chisel3._
import chisel3.util.Cat

class LFSR extends Module {
  // Declare input-output interface signals
  val io = IO(new Bundle {
    // Clock and reset are default,
    // No other inputs necessary
    // lfsr_6 and lfsr_3r will have the random values
    val lfsr_6 = Output(UInt(6.W))
    val lfsr_3r = Output(UInt(3.W))
  })

  // Declare the 6-bit register and initialize to 000001
  val D0123456 = RegInit(1.U(6.W)) //will init at reset

  // Next clk value is XOR of 2 MSBs as LSB concatenated with left shift rest
  // Example: 010010 => 100101 = Cat('10010','0^1')
  val nxt_D0123456 = Cat(D0123456(4, 0), D0123456(5) ^ D0123456(4))

  // Update 6-bit register will happen in sync with clk
  D0123456 := nxt_D0123456

  // Assign outputs
  io.lfsr_6 := D0123456
  // lfsr_3r in reverse order just for fun
  io.lfsr_3r := Cat(D0123456(1), D0123456(3), D0123456(5))
}

object LFSR {
  def apply(): LFSR = Module(new LFSR())
}