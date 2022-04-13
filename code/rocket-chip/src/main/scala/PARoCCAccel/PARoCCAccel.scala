package PARoCCAccel
// import freechips.rocketchip.config.{Config}
import Chisel._
import freechips.rocketchip.tile._ // import LazyRoCC
import freechips.rocketchip.config._ // import Config object
import freechips.rocketchip.diplomacy._ // import LazyModule
import aes._
class PA(val w : Int) extends Module{
    val io = IO(new Bundle{
        val in1 = Flipped(Valid(UInt(w.W)))
        val in2 = Flipped(Valid(UInt(w.W)))
        val funct = Flipped(Valid(UInt(w.W)))
        val out = Decoupled(UInt(w.W))
        val interrupt = Bool(OUTPUT)
    })
    val x = Reg(UInt(128.W))
    val y = Reg(UInt(w.W))
    val xplusy = Reg(UInt(w.W))
    val interrupt = RegInit(false.B)
    val s_idle::s_dataIn::s_beforeencry::s_midencry::s_aut::s_middeencry::s_enddeencry::s_finish::Nil = Enum(8) // 定义8个state
    val state = RegInit(s_idle)
    // val mask = Bits("h0000007FFFFFFFFF",64) //sv39
    val mask = Bits("h0000ffffffffffff",64) //sv48
    val encryfinish = RegInit(false.B)
    state := MuxCase(state,Seq(
        (((state===s_idle)&&io.in1.valid&&io.in2.valid&&io.funct.valid) -> s_dataIn),
        (((state===s_dataIn)&& (io.funct.bits===0.U)) -> s_beforeencry),
        (((state===s_dataIn)&& (io.funct.bits===1.U)) -> s_aut),
        (((state===s_beforeencry)&&(encryfinish===true.B)) -> s_midencry),
        ((state===s_midencry) -> s_finish),
        (((state===s_aut)&&(encryfinish===true.B)) -> s_middeencry),
        ((state===s_middeencry) -> s_enddeencry),
        ((state===s_enddeencry) -> s_finish),
        (((state===s_finish)&&io.out.ready) -> s_idle)))

    when(state===s_dataIn){
        x := io.in1.bits
        y := io.in2.bits
    }

    val encreg = RegInit(0.U(128.W))
    // val encmask=Bits("h0000000000000000FFFFFF8000000000",128) // sv39
    val encmask=Bits("h0000000000000000ffff000000000000",128) // sv48
    val enctext=RegInit(0.U(128.W))
    // val enckey1=Bits("h12345678912345678912345678912345",128)
    private val expandedKeyMemType = "ROM" // ROM or Mem or SyncReadMem works
    private val SubBytes_SCD = false
    private val InvSubBytes_SCD = false
    private val Nk = 4
    private val unrolled = 14
    val encry = Module(new AES(Nk, unrolled, SubBytes_SCD, InvSubBytes_SCD, expandedKeyMemType))
    encry.io.AES_mode:=0.U
    val test1 = RegInit(0.U(64.W))

    when(state===s_beforeencry){
        // Build up plaintext and generate ciphertext
        encry.io.AES_mode:=2.U
        // enctext:=(((x & mask)<<64)|(y & mask)) // plaintext
        enctext:=((x << 64) | (y & mask)) // plaintext
        encry.io.input_text:=(Vec(16, UInt(8.W))).fromBits(enctext)
        when(encry.io.output_valid){
            encryfinish:=true.B
            encreg := encry.io.output_text.asUInt // ciphertext
        }
        // printf("pac plaintext: %d\n", enctext)
        // printf("pac ciphertext: %d\n", encreg)
    }

    when(state===s_midencry){
        // Generate PAC and store it into the unused bits
        // encreg := encry.io.output_text.asUInt
        xplusy := ((encmask & encreg)|(y & mask))
        encryfinish := false.B
        // encry.io.AES_mode := 0.U
        encreg:=0.U
        encry.reset:=true.B
    }

    when(state===s_aut){
        // Build up plaintext and generate ciphertext
        encry.io.AES_mode:=2.U
        // enctext:=(((x & mask)<<64)|(y & mask)) // plaintext
        enctext:=((x << 64) | (y & mask)) // plaintext
        encry.io.input_text:=(Vec(16, UInt(8.W))).fromBits(enctext)
        when(encry.io.output_valid){
            encryfinish:=true.B
            encreg:=encry.io.output_text.asUInt
        }
        // printf("aut plaintext: %d\n", enctext)
        // printf("aut miwen: %d\n", encreg)
    }

    val auth=RegInit(false.B)
    when(state===s_middeencry){   
        // Authentication
        encryfinish:=false.B
        auth:= Mux(y === ((encmask & encreg)|(y & mask)),true.B,false.B)
    }

    io.interrupt := interrupt
    io.out.bits := xplusy
    io.out.valid := state === s_finish
    // printf("pac xplusy: %d\n", xplusy)

    when(state===s_enddeencry){
        when(auth===true.B){
            xplusy := y & mask
            printf("Success!")
        }
        .otherwise{
            xplusy := y & mask
            interrupt := true.B
            printf("Failed!")
        }
        encreg:=0.U
        encry.reset:=true.B
    }
}

class PARoCCAccel(opcodes: OpcodeSet, val w : Int)(implicit p: Parameters) extends LazyRoCC(opcodes){
    override lazy val module = new LazyRoCCModuleImp(this){
        // LazyRoCCModuleImp
        val rd = RegInit(0.U(5.W))
        val rs1Value = RegInit(0.U(w.W))
        val rs1Enable = RegInit(false.B)
        val rs2Value = RegInit(0.U(w.W))
        val rs2Enable = RegInit(false.B)

        val busy = RegInit(false.B)
        val interrupt=RegInit(false.B)
        val canResp = RegInit(false.B)
        val funct = RegInit(0.U(w.W))
        val functEnable = RegInit(false.B)
        io.cmd.ready := !busy
        io.busy := busy
        io.interrupt := interrupt
        val canDecode = io.cmd.fire()
        when(canDecode){ // rocket-core send an instruction
            busy := true.B
            rs1Value := io.cmd.bits.rs1
            rs1Enable := true.B
            rs2Value := io.cmd.bits.rs2
            rs2Enable := true.B
            rd := io.cmd.bits.inst.rd
            funct := io.cmd.bits.inst.funct
            functEnable := true.B
            
        }
    
        val pa = Module(new PA(w))
        pa.io.in1.bits := rs1Value
        pa.io.in2.bits := rs2Value
        pa.io.in1.valid := rs1Enable
        pa.io.in2.valid := rs2Enable
        pa.io.funct.bits := funct
        pa.io.funct.valid := functEnable

        val Res = RegInit(0.U(w.W))
        pa.io.out.ready := Mux(pa.io.out.valid, true.B, false.B) 
        when(pa.io.out.valid){
            Res := pa.io.out.bits
            canResp := true.B
            functEnable := false.B
            io.interrupt := pa.io.interrupt
        }

        io.resp.valid := canResp
        io.resp.bits.rd := rd
        io.resp.bits.data := Res
             
       // io.resp.valid := true.B
       // io.resp.bits.rd := rd
       // io.resp.bits.data := rs2Value
        
        when(io.resp.fire()){
            canResp := false.B
            busy := false.B
            rs1Enable := false.B
            rs2Enable := false.B
            rs1Value := 0.U
            rs2Value := 0.U
            io.interrupt := false.B
            funct := 0.U
            // Res := 0.U
        }
    }
}