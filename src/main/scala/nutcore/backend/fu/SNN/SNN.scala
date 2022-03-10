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

class SNNIO extends FunctionUnitIO{
    val imm = Input(UInt(XLEN.W))
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

    // spike process module
    val ssp = Module(new SpikeProc(XLEN))
    ssp.io.src1 := src1
    ssp.io.src2 := src2
    ssp.io.imm := imm

    // neuron inputs
    val vInit = RegInit(0.U(XLEN.W))
    val neurPreS = RegInit(0.U(XLEN.W))
    val vTh = RegInit(0.U(XLEN.W))
    val leaky = RegInit(0.U(XLEN.W))
    val spike = RegInit(0.U(1.W))

    // neuron module
    val neurn = Module(new NeurModule(XLEN))
    neurn.io.neurPreS := src1
    neurn.io.vIn := src2
    neurn.io.imm := imm
    neurn.io.vInit := vInit
    neurn.io.vTh := vTh
    neurn.io.leaky := leaky
    spike := neurn.io.spike

    // STDP module
    //val stdp = Module(new One_bit_STDP)

    val res = Mux(func === "b010".U, neurn.io.res, ssp.io.res)

    io.out.bits := res
    io.out.valid := DontCare
    io.out.ready := DontCare
    //List(SpikeProc.io, NeurModule.io, SynModule.io).map{ case x =>
    //    x.out.ready := io.out.ready
    //}
}