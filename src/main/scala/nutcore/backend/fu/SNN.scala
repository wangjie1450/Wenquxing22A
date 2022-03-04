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
    def isLdOp(func3: UInt):Bool  = func3(2) & (func3(0) ^ func3(1))
    def isSld(func3: UInt): Bool  = isLdOp(func3) & func3(1)
    def isInit(func3: UInt):Bool  = func3(0) & func3(1) & func3(2)
}

class SpikeProc(val len: Int) extends NutCoreModule{
    val io = IO(new NutCoreBundle{
        val src1 = Input(UInt(len.W))
        val src2 = Input(UInt(len.W))
        val imm  = Input(UInt(len.W))
        val res  = Output(UInt(len.W))
    })

    val (src1, src2, imm) = (io.src1, io.src2, io.imm)
    val sppRes = RegInit(0.U(len.W))
    val regPopRes = RegInit(0.U(len.W))
    sppRes := src1 & src2
    regPopRes := PopCount(sppRes)

    def isPop(func7: UInt): Bool = !func7(1) & func7(0)
    io.res := Mux(isPop(imm), regPopRes, sppRes)
}

class NeurIO(len: Int) extends Bundle{
    val in = Flipped(DecoupledIO(Vec(3, Output(UInt(len.W)))))
    val out = DecoupledIO(Output(UInt(len.W)))
}

class NeurModule(len: Int) extends NutCoreModule{
    val io = IO(new NeurIO(len))

    val (vInit, vin, nuerState) = (io.in.bits(0), io.in.bits(1), io.in.bits(2))
    
}

//class memOpdecode

class SNNIO extends FunctionUnitIO{
    val imm = Input(UInt(XLEN.W))
    //val dmem = new SimpleBusUC(addrBits = VAddrBits, userBits = DCacheUserBundleWidth)
    //val dtlb = new SimpleBusUC(addrBits = VAddrBits, userBits = DCacheUserBundleWidth)
}

class SNN extends NutCoreModule{
    val io = IO(new SNNIO)

    val imm = io.imm
    val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)    
    def access(valid: Bool, src1: UInt, src2: UInt, func: UInt):UInt = {
        this.valid := valid
        this.src1 := src1
        this.src2 := src2
        this.func := func
        io.out.bits
    }
    io.in.ready := DontCare

    val isEnOnl = SNNOpType.isSld(imm)
    val isLdOp = SNNOpType.isLdOp(func)
    val isSld = SNNOpType.isSld(func)
    val isInit = SNNOpType.isInit(func)

    val ssp = Module(new SpikeProc(XLEN))
    ssp.io.src1 := src1
    ssp.io.src2 := src2
    ssp.io.imm := imm
    val res = ssp.io.res

    io.out.bits := res
    io.out.valid := DontCare
    io.out.ready := DontCare
    //List(SpikeProc.io, NeurModule.io, SynModule.io).map{ case x =>
    //    x.out.ready := io.out.ready
    //}
}