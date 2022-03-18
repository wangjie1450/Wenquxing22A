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
    def nst   = "b011".U
    def sst   = "b100".U
    def nld   = "b101".U
    def sld   = "b110".U
    def sinit = "b111".U    

    def isDOp(func: UInt): Bool = !func(1) & !func(2)
    def isInit(func: UInt):Bool = !isDOp(func) & func(0)
}

object SNNRF{
    def vinit = "b00".U
    def vth = "b01".U
    def nr  = "b10".U
    def sr  = "b11".U
}

class SNNIO extends FunctionUnitIO{
    val imm = Input(UInt(XLEN.W))
    val toSNNvth = Input(UInt(XLEN.W))
    val toSNNvinit = Input(UInt(XLEN.W))
}

class SNN extends NutCoreModule{
    val io = IO(new SNNIO)

    val imm = io.imm
    val vTh = io.toSNNvth
    val vinit = io.toSNNvinit
    val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)    
    def access(valid: Bool, src1: UInt, src2: UInt, func: UInt):UInt = {
        this.valid := valid
        this.src1 := src1
        this.src2 := src2
        this.func := func
        io.out.bits
    }

    val option = Mux(SNNOpType.isDOp(func), Cat(imm, func), func)

    io.in.ready := DontCare

    // spike process module
    val ssp = Module(new SpikeProc(XLEN))
    ssp.io.src1 := src1
    ssp.io.src2 := src2
    ssp.io.imm := imm

    // neuron inputs
    val neurPreS = RegInit(UInt(XLEN.W), 0.U)
    val spike = RegInit(UInt(1.W), 0.U)
    val output = RegInit(UInt(XLEN.W), 0.U)

    // neuron module
    val neuron = Module(new NeurModule(XLEN))
    neuron.io.neurPreS := src2
    neuron.io.vIn := ssp.io.popres
    neuron.io.vInit := vinit
    neuron.io.vTh := vTh
    neuron.io.leaky := src1
    spike := neuron.io.spike

    // STDP module
    val stdp = Module(new STDP(XLEN))
    stdp.io.en := (SNNOpType.isInit(func) & imm(0)).asUInt
    stdp.io.input := src2
    stdp.io.synapse := src1
    stdp.io.output := output
    

    val res = LookupTree(option, List(
        SNNOpType.ands  ->  ssp.io.andres,
        SNNOpType.sge   ->  neuron.io.spike,
        SNNOpType.rpop  ->  ssp.io.popres,
        SNNOpType.sls   ->  neuron.io.output,
        SNNOpType.sup   ->  stdp.io.res,
        SNNOpType.nadd  ->  neuron.io.res
    ))

    io.out.bits := res
    io.out.valid := DontCare
    io.out.ready := DontCare
    //List(SpikeProc.io, NeurModule.io, SynModule.io).map{ case x =>
    //    x.out.ready := io.out.ready
    //}
}