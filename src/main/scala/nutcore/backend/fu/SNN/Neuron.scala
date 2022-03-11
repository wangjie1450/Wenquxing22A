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

class NeuronIO(val len: Int) extends NutCoreBundle{
    val vInit = Input(UInt(len.W))
    val vIn = Input(UInt(len.W))
    val neurPreS = Input(UInt(len.W))
    val leaky = Input(UInt(len.W))
    val vTh = Input(UInt(len.W))
    val spike = Output(UInt(1.W))
    val res = Output(UInt(len.W))
    val output = Output(UInt(len.W))
}

class NeurModule(len: Int) extends NutCoreModule{
    val io = IO(new NeuronIO(len))

    val vInit = io.vInit
    val vIn = io.vIn
    val neurPreS = io.neurPreS
    val leaky = ~io.leaky + 1.U
    val vTh = io.vTh

    val wlt = Module(new WallaceTree)
    wlt.io.in(0) := leaky(7,0)
    wlt.io.in(1) := vIn(7,0)
    wlt.io.in(2) := neurPreS(7,0)

    def isSpike(neurnexts: UInt, vTh: UInt): Bool ={
        Mux(neurnexts >= vTh, true.B, false.B)
    }

    val neurnexts = RegInit(0.U(len.W))
    val spike = (isSpike(wlt.io.sum,vTh)|wlt.io.overf)
    val outputr = RegInit(0.U(len.W))

    neurnexts := Mux(spike, vInit, wlt.io.sum)
    io.output := (outputr + spike.asUInt) << 1

    io.res := neurnexts
    io.spike := spike.asUInt
}