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
    def ands  = "b00000_000".U 
    def sge   = "b00001_000".U
    def rpop  = "b00000_001".U
    def sls   = "b00001_001".U
    def vth   = "b00010_001".U
    def sup   = "b00011_001".U
    def nadd  = "b010".U
    def sinit = "b111".U    

    def isDOp(func: UInt): Bool = !func(1) && !func(2)
    def isInit(func: UInt):Bool = !isDOp(func) && func(0)
}

object SNNRF{
    def vinit = "b00".U
    def vth = "b01".U
    def nr  = "b10".U
    def sr  = "b11".U
}

object  SNNCalcType{
    def ssp     = "b00".U
    def neuron  = "b01".U
    def stdp    = "b10".U 
    def none    = "b11".U
}

class SNNIO extends FunctionUnitIO{
    val imm = Input(UInt(XLEN.W))
    val toSNNvth = Input(UInt(XLEN.W))
    val toSNNvinit = Input(UInt(XLEN.W))
}

class SNN extends NutCoreModule{
    val io = IO(new SNNIO)

    val imm = io.imm
    val toSNNvth = io.toSNNvth
    val vinit = io.toSNNvinit
    val vleaky = toSNNvth(15,8)
    val vth = toSNNvth(7,0)
    val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)    
    def access(valid: Bool, src1: UInt, src2: UInt, func: UInt):UInt = {
        this.valid := valid
        this.src1 := src1
        this.src2 := src2
        this.func := func
        io.out.bits
    }

    val option = Mux(SNNOpType.isDOp(func), Cat(imm, func), func)

    val calcUnit = LookupTree(option, List(
        SNNOpType.ands        -> SNNCalcType.ssp,
        SNNOpType.sge         -> SNNCalcType.neuron,
        SNNOpType.rpop        -> SNNCalcType.ssp,
        SNNOpType.sls         -> SNNCalcType.neuron,
        SNNOpType.vth         -> SNNCalcType.none,
        SNNOpType.sup         -> SNNCalcType.stdp,
        SNNOpType.nadd        -> SNNCalcType.neuron,
        SNNOpType.sinit       -> SNNCalcType.none // addsi
    ))

    // spike process module
    val ssp = Module(new SpikeProc(XLEN))
    ssp.io.in.bits.src1 := src1
    ssp.io.in.bits.src2 := src2
    ssp.io.in.bits.op := option
    ssp.io.out.ready := io.out.ready
    ssp.io.in.valid := valid && (calcUnit === SNNCalcType.ssp)

    // neuron inputs
    val neurPreS = RegInit(UInt(XLEN.W), 0.U)
    val spike = RegInit(UInt(1.W), 0.U)
    val output = RegInit(UInt(XLEN.W), 0.U)

    // neuron module
    val neuron = Module(new NeurModule(XLEN))
    neuron.io.in.bits.src1  := src1
    neuron.io.in.bits.src2  := src2
    neuron.io.in.bits.vth   := vth
    neuron.io.in.bits.vleaky := vleaky
    neuron.io.in.bits.vinit := vinit
    neuron.io.in.bits.option := option
    neuron.io.out.ready     := io.out.ready
    neuron.io.in.valid      := valid && (calcUnit === SNNCalcType.neuron)

    // STDP module
    val stdp = Module(new STDP(XLEN))
    stdp.io.en := (SNNOpType.isInit(func) & imm(0)).asUInt
    stdp.io.input := src2
    stdp.io.synapse := src1
    stdp.io.output := output
    

    val res = LookupTree(option, List(
        SNNOpType.ands  ->  ssp.io.out.bits,
        SNNOpType.sge   ->  neuron.io.out.bits.spike,
        SNNOpType.rpop  ->  ssp.io.out.bits,
        SNNOpType.sls   ->  neuron.io.out.bits.output,
        SNNOpType.sup   ->  stdp.io.res,
        SNNOpType.nadd  ->  neuron.io.out.bits.vneuron
    ))

    io.in.ready := LookupTree(calcUnit, List(
        SNNCalcType.ssp         -> ssp.io.in.ready,
        SNNCalcType.neuron      -> neuron.io.in.ready
        //SNNCalcType.stdp        -> stdp.in.ready
    ))

    io.out.bits := res
    io.out.valid := ssp.io.out.valid
}