/**************************************************************************************
* Copyright (c) 2020 Institute of Computing Technology, CAS
* Copyright (c) 2020 University of Chinese Academy of Sciences
* 
* NutShell is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2. 
* You may obtain a copy of Mulan PSL v2 at:
*             http://license.coscl.org.cn/MulanPSL2 
* 
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER 
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR 
* FIT FOR A PARTICULAR PURPOSE.  
*
* See the Mulan PSL v2 for more details.  
***************************************************************************************/

package nutcore

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import top.Settings

object SNNOpType{
    def ands  = "b0000".U 
    def sge   = "b0001".U
    def rpop  = "b0010".U
    def sls   = "b0011".U
    def drd   = "b0100".U
    def sup   = "b0101".U
    def nadd  = "b0110".U
    def nst   = "b0111".U
    def sst   = "b1000".U
    def nld   = "b1001".U
    def sld   = "b1010".U
    def sinit = "b1011".U    

    def isEnOnl(imm: UInt):Bool  = imm(0)
    def isLdOp(func: UInt):Bool  = func(2) & (func(0) ^ func(1))
    def isSld(func: UInt): Bool  = isLdOp(func) & func(1)
    def isInit(func: UInt):Bool  = func(0) & func(1) & func(2)
}

class ModuleIO(len: Int) extends Bundle{
    val in = Flipped(DecoupledIO(Vec(2, Output(UInt(len.W)))
    val out = DecoupledIO(Output(UInt(len.W)))
}

class spikeHander(len: Int) extends NutCoreModule{
    val io = IO(new ModuleIO(len))

    def SNNInPipe[T <: data](a: T) = RegNext(a)
    def SNNOutPipe[T <: data](a: T) = RegNext(RegNext(a))
    val sppRes = (SNNInPipe(io.in.bits(0)).asUInt && SNNInPipe(io.in.bits(1)).asUInt)
    io.out.bits := SNNOutPipe(sppRes).asUInt
    io.out.valid := SNNOutPipe(io.in.fire())

    val busy = RegInit(false.B)
    when (io.in.valid && !busy) { busy := true.B }
    when (io.out.valid) { busy := false.B }
    io.in.ready := !busy
}

class neurIO(len: Int) extends Bundle{
    val in = Flipped(DecoupledIO(Vec(3, Output(UInt(len.W)))))
    val out = DecoupledIO(Output(UInt(len.W)))
}

class neurModule(len: Int = 64) extends NutCoreModule{
    val io = IO(new neurIO(len))

}

//class memOpdecode

class SNNIO extends FunctionUnitIO{
    val imm = Input(UInt(XLEN.W))
    //val dmem = new SimpleBusUC(addrBits = VAddrBits, userBits = DCacheUserBundleWidth)
    //val dtlb = new SimpleBusUC(addrBits = VAddrBits, userBits = DCacheUserBundleWidth)
}

class SNN extends NutCoreModule{
    val io = IO(new SNNIO)

    val (valid, src1, src2, imm, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.imm, io.in.bits.func)    
    def access(valid: Bool, src1: UInt, src2: UInt, imm: UInt, func: UInt){
        this.valid := valid
        this.src1 := src1
        this.src2 := src2
        this.imm := imm
        this.func := func
        io.out.bits
    }

    val isEnOnl = SNNOpType.isSld(imm)
    val isLdOp = SNNOpType.isLdOp(func)
    val isSld = SNNOpType.isSld(func)
    val isInit = SNNOpType.isInit(func)

    

}