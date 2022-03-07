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

    def isPop(func7: UInt): Bool = func7(0)
    io.res := Mux(isPop(imm), regPopRes, sppRes)
}

// neuron update
// full adder
class FullAdder extends Module{
    val io = IO(new Bundle{
        val in = Vec(2,Input(UInt(1.W)))
        val cin = Input(UInt(1.W)) 
        val sum = Output(UInt(1.W))
        val co = Output(UInt(1.W))
    })
    io.co := io.in(1) & io.in(0) & io.cin
    io.sum := io.in(0) ^ io.in(1) ^io.cin
}

// Wallace tree adder
class WallaceTree(len: Int = 8)extends Module{
    val io = IO(new Bundle{
        val in = Vec(3, Input(UInt(len.W)))
        val out = Output(UInt(len.W))
    })


    val adder = VecInit(Seq.fill(8){Module(new FullAdder).io})
    for (i <- 0 to 7){
        for (j <- 0 to 2){
                adder(i).in(j) := io.in(j)
        }
    }

    val sum = 0.U
    val co = 0.U
    for(i <- 0 to 8){
        sum := Cat(adder(i).sum)
        co := Cat(adder(i).co)
    }
    val res = (sum + co) << 1.U
    io.out := res(7,0)
}

class NeurModule(len: Int) extends NutCoreModule{
    val io = IO(new NutCoreBundle{
        val vInit = Input(UInt(len.W))
        val vIn = Input(UInt(len.W))
        val neurPreS = Input(UInt(len.W))
        val leaky = Input(UInt(len.W))
        val vTh = Input(UInt(len.W))
        val imm = Input(UInt(len.W))
        val Spike = Output(UInt(1.W))
        val res = Output(UInt(len.W))
    })

    val (vInit, vIn, neurPreS) = (io.vInit, io.vIn, io.neurPreS)
    val wlt = Module(new WallaceTree)

    val leaky = ~io.leaky + 1.U
    val vTh = io.vTh
    wlt.io.in(0) := leaky(7,0)
    wlt.io.in(1) := vIn(7,0)
    wlt.io.in(2) := neurPreS(7,0)

    def isSpike(neurnexts: UInt, vTh: UInt): Bool ={
        Mux(neurnexts >= vTh, true.B, false.B)
    }
    val neurnexts = wlt.io.out
    val spike = Mux(isSpike(neurnexts = neurnexts, vTh = io.vTh), 1.U, 0.U)
    val outputr = RegInit(0.U(len.W))
    val outres = (outputr + spike) << 1
    def isComp(func7: UInt): Bool = func7(0) & !func7(1)
    io.res := Mux(isComp(io.imm), outres, neurnexts)
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

    val vInit = 0.U(XLEN.W)
    val vIn = 0.U(XLEN.W)
    val neurPreS = 0.U(XLEN.W)
    val vTh = 0.U(XLEN.W)
    val leaky = 0.U(XLEN.W)


    val neurn = Module(new NeurModule(XLEN))
    neurn.io.imm := imm
    neurn.io.vInit := vInit
    neurn.io.vIn := vIn
    neurn.io.neurPreS := src1
    neurn.io.leaky := leaky


    val res = Mux(func === "b010".U, neurn.io.res, ssp.io.res)

    io.out.bits := res
    io.out.valid := DontCare
    io.out.ready := DontCare
    //List(SpikeProc.io, NeurModule.io, SynModule.io).map{ case x =>
    //    x.out.ready := io.out.ready
    //}
}