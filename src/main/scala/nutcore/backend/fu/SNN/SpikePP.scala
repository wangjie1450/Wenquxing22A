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

class SpikeProc(val len: Int) extends NutCoreModule{
    val io = IO(new NutCoreBundle{
        val src1 = Input(UInt(len.W))
        val src2 = Input(UInt(len.W))
        val imm  = Input(UInt(len.W))
        val andres  = Output(UInt(len.W))
        val popres = Output(UInt(len.W))
    })

    val (src1, src2, imm) = (io.src1, io.src2, io.imm)
    val sppRes = RegInit(0.U(len.W))
    val regPopRes = RegInit(0.U(len.W))
    sppRes := src1 & src2
    regPopRes := PopCount(sppRes)

    io.andres := sppRes
    io.popres := regPopRes
}