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

import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.icfg.CallEdge;
import pascal.taie.analysis.graph.icfg.CallToReturnEdge;
import pascal.taie.analysis.graph.icfg.NormalEdge;
import pascal.taie.analysis.graph.icfg.ReturnEdge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;

/**
 * Implementation of interprocedural constant propagation for int values.
 */
public class InterConstantPropagation extends
        AbstractInterDataflowAnalysis<JMethod, Stmt, CPFact> {

    public static final String ID = "inter-constprop";

    private final ConstantPropagation cp;

    public InterConstantPropagation(AnalysisConfig config) {
        super(config);
        cp = new ConstantPropagation(new AnalysisConfig(ConstantPropagation.ID));
    }

    @Override
    public boolean isForward() {
        return cp.isForward();
    }

    @Override
    public CPFact newBoundaryFact(Stmt boundary) {
        IR ir = icfg.getContainingMethodOf(boundary).getIR();
        return cp.newBoundaryFact(ir.getResult(CFGBuilder.ID));
    }

    @Override
    public CPFact newInitialFact() {
        return cp.newInitialFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        cp.meetInto(fact, target);
    }

    @Override
    protected boolean transferCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        if(out == null) return false;
        return out.copyFrom(in);
    }

    @Override
    protected boolean transferNonCallNode(Stmt stmt, CPFact in, CPFact out) {
        return cp.transferNode(stmt, in, out);
    }

    @Override
    protected CPFact transferNormalEdge(NormalEdge<Stmt> edge, CPFact out) {
        return out;
    }

    @Override
    protected CPFact transferCallToReturnEdge(CallToReturnEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        //创建出口状态的副本
        if(out == null) return newInitialFact();
        CPFact temp = out.copy();
        //处理调用语句定义的返回值
        edge.getSource().getDef().ifPresent(l->{
            //只处理变量类型的返回值
            if(l instanceof Var retVar){
                //清除返回变量的常量值
                temp.remove(retVar);
            }
        });
        return temp;
    }

    @Override
    protected CPFact transferCallEdge(CallEdge<Stmt> edge, CPFact callSiteOut) {
        // 处理入口状态
        CPFact cpf = newInitialFact();
        if(callSiteOut == null) return cpf;
        // 获取被调用方法
        JMethod jmethod = edge.getCallee();
        // 获取调用点信息
        Stmt stmt = edge.getSource();
        // 仅处理invoke类型调用
        if (!(stmt instanceof Invoke invoke)) {
            return cpf;
        }
        // 处理实参
        InvokeExp invokeExp = invoke.getRValue();
        // 带越界保护的参数遍历
        for (int i = 0; i < invokeExp.getArgCount(); i++) {
            // 获取实参
            Var arg = invokeExp.getArg(i);
            // 获取形参
            Var param = jmethod.getIR().getParam(i);
            // 安全获取并传播常量值
            Value val = callSiteOut.get(arg);
            cpf.update(param, val);
        }

        return cpf;
    }

    @Override
    protected CPFact transferReturnEdge(ReturnEdge<Stmt> edge, CPFact returnOut) {
        // 初始化返回状态
        CPFact cpf = newInitialFact();
        if(returnOut == null) return  cpf;
        // 仅处理有返回值的调用点
        edge.getCallSite().getDef().ifPresent(lValue -> {
            // 1. 计算合并后的返回值
            Value retValue = Value.getUndef();
            for (Var i : edge.getReturnVars()) {
                retValue = cp.meetValue(retValue, returnOut.get(i));
            }
            // 2. 更新到调用点的接收变量
            if (lValue instanceof Var lVar) {
                cpf.update(lVar, retValue);
            }
        });
        return cpf;
    }
}
