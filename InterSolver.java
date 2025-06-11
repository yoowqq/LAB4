/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.inter;

import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.icfg.ICFG;
import pascal.taie.analysis.graph.icfg.ICFGEdge;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.SetQueue;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Solver for inter-procedural data-flow analysis.
 * The workload of inter-procedural analysis is heavy, thus we always
 * adopt work-list algorithm for efficiency.
 */
class InterSolver<Method, Node, Fact> {

    private final InterDataflowAnalysis<Node, Fact> analysis;

    private final ICFG<Method, Node> icfg;

    private DataflowResult<Node, Fact> result;

    private Queue<Node> workList;

    InterSolver(InterDataflowAnalysis<Node, Fact> analysis,
                ICFG<Method, Node> icfg) {
        this.analysis = analysis;
        this.icfg = icfg;
    }

    DataflowResult<Node, Fact> solve() {
        result = new DataflowResult<>();
        initialize();
        doSolve();
        return result;
    }

    private void initialize() {
        // TODO - finish me
        //遍历控制流图里面的所有节点
        for(Node node : icfg.getNodes()){
            result.setInFact(node, analysis.newInitialFact());
            result.setOutFact(node, analysis.newInitialFact());
        }
        //入口方法特殊处理
        icfg.entryMethods().forEach(i->{
            Node entryNode = icfg.getEntryOf(i);
            result.setInFact(entryNode, analysis.newBoundaryFact(entryNode));
            result.setOutFact(entryNode, analysis.newBoundaryFact(entryNode));
        });
    }

    private void doSolve() {
        // TODO - finish me
        //初始化工作列表
        workList = new ArrayDeque<>();
        //将控制流图的节点添加到工作列表
        for(Node node : icfg.getNodes()){
             workList.add(node);
        }
        //遍历工作列表
        while(!workList.isEmpty()){
            //弹出节点
            Node node = workList.poll();
            //合并前驱节点
            mergePredecessors(node);
            //如果输出状态变化
            if(analysis.transferNode(node, result.getInFact(node), result.getOutFact(node) )){
                //将后继节点添加到工作列表
                workList.addAll(icfg.getSuccsOf(node));
            }
        }
    }

    private void mergePredecessors(Node node) {
        for(ICFGEdge<Node> i : icfg.getInEdgesOf(node)){
            analysis.meetInto(analysis.transferEdge(i, result.getOutFact(i.getSource())), result.getInFact(node));
        }
    }
}
