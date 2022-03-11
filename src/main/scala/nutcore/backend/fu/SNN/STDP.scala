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

class STDPIO(val len: Int) extends NutCoreBundle{
    val en = Input(UInt(1.W))
    val input = Input(UInt(len.W))
    val synapse = Input(UInt(len.W))
    val output = Input(UInt(len.W))
    val res = Output(UInt(len.W))
}

class STDP(len: Int) extends NutCoreModule{
    val io = IO(new STDPIO(len))
    
    val (input, synapse, output) = (io.input, io.synapse, io.output) 
    val syn_new = Wire(Vec(len, UInt(1.W)))
    for (i <- 0 to (len - 1)){
        if(output(i) == 1.U)  syn_new(i) := input(i) && output(i)
        else    syn_new(i) := synapse(i)
    }

    io.res := Mux(io.en === 1.U, syn_new.asUInt, io.synapse)
}