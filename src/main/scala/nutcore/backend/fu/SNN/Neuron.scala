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

import utils._

class NeuronIO(val len: Int) extends NutCoreBundle{
    val in = Flipped(DecoupledIO(new Bundle{
        val src1        = Input(UInt(len.W))
        val src2        = Input(UInt(len.W))
        val vth         = Input(UInt(len.W))
        val vleaky      = Input(UInt(len.W))
        val vinit       = Input(UInt(len.W))
        val option      = Input(UInt(len.W))
    }))
    val out = DecoupledIO(new Bundle{
        val spike       = Output(Bool())
        val vneuron     = Output(UInt(len.W))
        val output      = Output(UInt(len.W))
    })
}

class NeurModule(len: Int) extends NutCoreModule{
    val io = IO(new NeuronIO(len))

    val (valid, src1, src2, option) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.option)

    val s_idle::s_nadd::s_sge::s_sls::Nil = Enum(4)

    val op          = RegInit(s_idle)
    val vneuron_reg = Reg(UInt(len.W))
    val vth_reg     = Reg(UInt(len.W))
    val vin_reg     = Reg(UInt(len.W))
    val vleaky_reg  = Reg(UInt(len.W))
    val vinit_reg   = Reg(UInt(len.W))
    val output_reg  = Reg(UInt(len.W))
    val spike       = Reg(Bool())
    val overf       = Reg(Bool())
    val naddRes     = RegInit(0.U(len.W))

    op := LookupTree(option, List(
        SNNOpType.nadd      -> s_nadd,
        SNNOpType.sge       -> s_sge,
        SNNOpType.sls       -> s_sls
    ))
    
    io.out.valid    := valid
    io.in.ready     := io.out.ready   

    when (op === s_idle) {
        io.out.valid    := valid
        io.in.ready     := io.out.ready
        vth_reg         := io.in.bits.vth
        vleaky_reg      := io.in.bits.vleaky
        vinit_reg       := io.in.bits.vinit
    }.elsewhen (op === s_nadd) {
        io.out.valid    := false.B
        io.in.ready     := false.B
        vneuron_reg     := io.in.bits.src2
        vin_reg         := io.in.bits.src1

        val wlt = Module(new WallaceTree)
        wlt.io.in(0)    := vleaky_reg(7,0)
        wlt.io.in(1)    := vin_reg(7,0)
        wlt.io.in(2)    := vneuron_reg(7,0)

        overf           := wlt.io.overf
        naddRes         := wlt.io.sum
        when(wlt.io.sum =/= 0.U){ op := s_idle }
    }.elsewhen (op === s_sge) {
        io.out.valid    := false.B
        io.in.ready     := false.B
        vneuron_reg     := io.in.bits.src1
        spike           := (vneuron_reg >= vth_reg) || overf
        op              := s_idle
    }.elsewhen (op === s_sls) {
        io.out.valid    := false.B
        io.in.ready     := false.B
        output_reg      := (io.in.bits.src1 << 1) + spike.asUInt
        op              := s_idle
    }.otherwise{
        io.out.valid    := false.B
        io.in.ready     := false.B
    }
    
    io.out.bits.spike   := spike
    io.out.bits.vneuron := vneuron_reg
    io.out.bits.output  := output_reg
}