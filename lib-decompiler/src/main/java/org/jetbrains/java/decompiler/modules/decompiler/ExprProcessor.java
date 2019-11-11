/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler;

import com.duy.java8.util.DMap;
import com.duy.java8.util.function.Function;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ArrayExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.MonitorExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.attr.StructBootstrapMethodsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExprProcessor implements CodeConstants {
    public static final String UNDEFINED_TYPE_STRING = "<undefinedtype>";
    public static final String UNKNOWN_TYPE_STRING = "<unknown>";
    public static final String NULL_TYPE_STRING = "<null>";

    private static final Map<Integer, Integer> mapConsts = new HashMap<>();
    private static final VarType[] consts = {
            VarType.VARTYPE_INT, VarType.VARTYPE_FLOAT, VarType.VARTYPE_LONG, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_CLASS, VarType.VARTYPE_STRING
    };
    private static final VarType[] varTypes = {
            VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT
    };
    private static final VarType[] arrTypes = {
            VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT,
            VarType.VARTYPE_BOOLEAN, VarType.VARTYPE_CHAR, VarType.VARTYPE_SHORT
    };
    private static final int[] func1 = {
            FunctionExprent.FUNCTION_ADD, FunctionExprent.FUNCTION_SUB, FunctionExprent.FUNCTION_MUL, FunctionExprent.FUNCTION_DIV,
            FunctionExprent.FUNCTION_REM
    };
    private static final int[] func2 = {
            FunctionExprent.FUNCTION_SHL, FunctionExprent.FUNCTION_SHR, FunctionExprent.FUNCTION_USHR, FunctionExprent.FUNCTION_AND,
            FunctionExprent.FUNCTION_OR, FunctionExprent.FUNCTION_XOR
    };
    private static final int[] func3 = {
            FunctionExprent.FUNCTION_I2L, FunctionExprent.FUNCTION_I2F, FunctionExprent.FUNCTION_I2D, FunctionExprent.FUNCTION_L2I,
            FunctionExprent.FUNCTION_L2F, FunctionExprent.FUNCTION_L2D, FunctionExprent.FUNCTION_F2I, FunctionExprent.FUNCTION_F2L,
            FunctionExprent.FUNCTION_F2D, FunctionExprent.FUNCTION_D2I, FunctionExprent.FUNCTION_D2L, FunctionExprent.FUNCTION_D2F,
            FunctionExprent.FUNCTION_I2B, FunctionExprent.FUNCTION_I2C, FunctionExprent.FUNCTION_I2S
    };
    private static final int[] func4 = {
            FunctionExprent.FUNCTION_LCMP, FunctionExprent.FUNCTION_FCMPL, FunctionExprent.FUNCTION_FCMPG, FunctionExprent.FUNCTION_DCMPL,
            FunctionExprent.FUNCTION_DCMPG
    };
    private static final int[] func5 = {
            IfExprent.IF_EQ, IfExprent.IF_NE, IfExprent.IF_LT, IfExprent.IF_GE, IfExprent.IF_GT, IfExprent.IF_LE
    };
    private static final int[] func6 = {
            IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPGT, IfExprent.IF_ICMPLE,
            IfExprent.IF_ACMPEQ, IfExprent.IF_ACMPNE
    };
    private static final int[] func7 = {IfExprent.IF_NULL, IfExprent.IF_NONNULL};
    private static final int[] func8 = {MonitorExprent.MONITOR_ENTER, MonitorExprent.MONITOR_EXIT};
    private static final int[] arrTypeIds = {
            CodeConstants.TYPE_BOOLEAN, CodeConstants.TYPE_CHAR, CodeConstants.TYPE_FLOAT, CodeConstants.TYPE_DOUBLE,
            CodeConstants.TYPE_BYTE, CodeConstants.TYPE_SHORT, CodeConstants.TYPE_INT, CodeConstants.TYPE_LONG
    };
    private static final int[] negIfs = {
            IfExprent.IF_NE, IfExprent.IF_EQ, IfExprent.IF_GE, IfExprent.IF_LT, IfExprent.IF_LE, IfExprent.IF_GT, IfExprent.IF_NONNULL,
            IfExprent.IF_NULL, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPLE,
            IfExprent.IF_ICMPGT, IfExprent.IF_ACMPNE, IfExprent.IF_ACMPEQ
    };
    private static final String[] typeNames = {"byte", "char", "double", "float", "int", "long", "short", "boolean"};

    static {
        mapConsts.put(opc_arraylength, FunctionExprent.FUNCTION_ARRAY_LENGTH);
        mapConsts.put(opc_checkcast, FunctionExprent.FUNCTION_CAST);
        mapConsts.put(opc_instanceof, FunctionExprent.FUNCTION_INSTANCEOF);
    }

    private final MethodDescriptor methodDescriptor;
    private final VarProcessor varProcessor;

    public ExprProcessor(MethodDescriptor md, VarProcessor varProc) {
        methodDescriptor = md;
        varProcessor = varProc;
    }

    // FIXME: Ugly code, to be rewritten. A tuple class is needed.
    private static String buildEntryPointKey(LinkedList<String> entrypoints) {
        if (entrypoints.isEmpty()) {
            return null;
        } else {
            StringBuilder buffer = new StringBuilder();
            for (String point : entrypoints) {
                buffer.append(point);
                buffer.append(":");
            }
            return buffer.toString();
        }
    }

    private static PrimitiveExprsList copyVarExprents(PrimitiveExprsList data) {
        ExprentStack stack = data.getStack();
        copyEntries(stack);
        return data;
    }

    public static void copyEntries(List<Exprent> stack) {
        for (int i = 0; i < stack.size(); i++) {
            stack.set(i, stack.get(i).copy());
        }
    }

    private static void collectCatchVars(Statement stat, FlattenStatementsHelper flatthelper, Map<String, VarExprent> map) {

        List<VarExprent> lst = null;

        if (stat.type == Statement.TYPE_CATCHALL) {
            CatchAllStatement catchall = (CatchAllStatement) stat;
            if (!catchall.isFinally()) {
                lst = catchall.getVars();
            }
        } else if (stat.type == Statement.TYPE_TRYCATCH) {
            lst = ((CatchStatement) stat).getVars();
        }

        if (lst != null) {
            for (int i = 1; i < stat.getStats().size(); i++) {
                map.put(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0], lst.get(i - 1));
            }
        }

        for (Statement st : stat.getStats()) {
            collectCatchVars(st, flatthelper, map);
        }
    }

    private static void initStatementExprents(Statement stat) {
        stat.initExprents();

        for (Statement st : stat.getStats()) {
            initStatementExprents(st);
        }
    }

    private static int nextMeaningfulOffset(BasicBlock block, int index) {
        InstructionSequence seq = block.getSeq();
        while (++index < seq.length()) {
            switch (seq.getInstr(index).opcode) {
                case opc_nop:
                case opc_istore:
                case opc_lstore:
                case opc_fstore:
                case opc_dstore:
                case opc_astore:
                    continue;
            }
            return block.getOldOffset(index);
        }
        return -1;
    }

    public static String getTypeName(VarType type) {
        return getTypeName(type, true);
    }

    public static String getTypeName(VarType type, boolean getShort) {
        int tp = type.type;
        if (tp <= CodeConstants.TYPE_BOOLEAN) {
            return typeNames[tp];
        } else if (tp == CodeConstants.TYPE_UNKNOWN) {
            return UNKNOWN_TYPE_STRING; // INFO: should not occur
        } else if (tp == CodeConstants.TYPE_NULL) {
            return NULL_TYPE_STRING; // INFO: should not occur
        } else if (tp == CodeConstants.TYPE_VOID) {
            return "void";
        } else if (tp == CodeConstants.TYPE_OBJECT) {
            String ret = buildJavaClassName(type.value);
            if (getShort) {
                ret = DecompilerContext.getImportCollector().getShortName(ret);
            }

            if (ret == null) {
                // FIXME: a warning should be logged
                ret = UNDEFINED_TYPE_STRING;
            }
            return ret;
        }

        throw new RuntimeException("invalid type");
    }

    public static String getCastTypeName(VarType type) {
        return getCastTypeName(type, true);
    }

    public static String getCastTypeName(VarType type, boolean getShort) {
        StringBuilder s = new StringBuilder(getTypeName(type, getShort));
        TextUtil.append(s, "[]", type.arrayDim);
        return s.toString();
    }

    public static PrimitiveExprsList getExpressionData(VarExprent var) {
        PrimitiveExprsList prlst = new PrimitiveExprsList();
        VarExprent vartmp = new VarExprent(VarExprent.STACK_BASE, var.getExprType(), var.getProcessor());
        vartmp.setStack(true);

        prlst.getLstExprents().add(new AssignmentExprent(vartmp, var.copy(), null));
        prlst.getStack().push(vartmp.copy());
        return prlst;
    }

    public static boolean endsWithSemicolon(Exprent expr) {
        int type = expr.type;
        return !(type == Exprent.EXPRENT_SWITCH ||
                type == Exprent.EXPRENT_MONITOR ||
                type == Exprent.EXPRENT_IF ||
                (type == Exprent.EXPRENT_VAR && ((VarExprent) expr).isClassDef()));
    }

    private static void addDeletedGotoInstructionMapping(Statement stat, BytecodeMappingTracer tracer) {
        if (stat instanceof BasicBlockStatement) {
            BasicBlock block = ((BasicBlockStatement) stat).getBlock();
            List<Integer> offsets = block.getInstrOldOffsets();
            if (!offsets.isEmpty() &&
                    offsets.size() > block.getSeq().length()) { // some instructions have been deleted, but we still have offsets
                tracer.addMapping(offsets.get(offsets.size() - 1)); // add the last offset
            }
        }
    }

    public static TextBuffer jmpWrapper(Statement stat, int indent, boolean semicolon, BytecodeMappingTracer tracer) {
        TextBuffer buf = stat.toJava(indent, tracer);

        List<StatEdge> lstSuccs = stat.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL);
        if (lstSuccs.size() == 1) {
            StatEdge edge = lstSuccs.get(0);
            if (edge.getType() != StatEdge.TYPE_REGULAR && edge.explicit && edge.getDestination().type != Statement.TYPE_DUMMYEXIT) {
                buf.appendIndent(indent);

                switch (edge.getType()) {
                    case StatEdge.TYPE_BREAK:
                        addDeletedGotoInstructionMapping(stat, tracer);
                        buf.append("break");
                        break;
                    case StatEdge.TYPE_CONTINUE:
                        addDeletedGotoInstructionMapping(stat, tracer);
                        buf.append("continue");
                }

                if (edge.labeled) {
                    buf.append(" label").append(edge.closure.id.toString());
                }
                buf.append(";").appendLineSeparator();
                tracer.incrementCurrentSourceLine();
            }
        }

        if (buf.length() == 0 && semicolon) {
            buf.appendIndent(indent).append(";").appendLineSeparator();
            tracer.incrementCurrentSourceLine();
        }

        return buf;
    }

    public static String buildJavaClassName(String name) {
        String res = name.replace('/', '.');

        if (res.contains("$")) { // attempt to invoke foreign member
            // classes correctly
            StructClass cl = DecompilerContext.getStructContext().getClass(name);
            if (cl == null || !cl.isOwn()) {
                res = res.replace('$', '.');
            }
        }

        return res;
    }

    public static TextBuffer listToJava(List<Exprent> lst, int indent, BytecodeMappingTracer tracer) {
        if (lst == null || lst.isEmpty()) {
            return new TextBuffer();
        }

        TextBuffer buf = new TextBuffer();

        for (Exprent expr : lst) {
            if (buf.length() > 0 && expr.type == Exprent.EXPRENT_VAR && ((VarExprent) expr).isClassDef()) {
                // separates local class definition from previous statements
                buf.appendLineSeparator();
                tracer.incrementCurrentSourceLine();
            }

            TextBuffer content = expr.toJava(indent, tracer);

            if (content.length() > 0) {
                if (expr.type != Exprent.EXPRENT_VAR || !((VarExprent) expr).isClassDef()) {
                    buf.appendIndent(indent);
                }
                buf.append(content);
                if (expr.type == Exprent.EXPRENT_MONITOR && ((MonitorExprent) expr).getMonType() == MonitorExprent.MONITOR_ENTER) {
                    buf.append("{}"); // empty synchronized block
                }
                if (endsWithSemicolon(expr)) {
                    buf.append(";");
                }
                buf.appendLineSeparator();
                tracer.incrementCurrentSourceLine();
            }
        }

        return buf;
    }

    public static ConstExprent getDefaultArrayValue(VarType arrType) {
        ConstExprent defaultVal;
        if (arrType.type == CodeConstants.TYPE_OBJECT || arrType.arrayDim > 0) {
            defaultVal = new ConstExprent(VarType.VARTYPE_NULL, null, null);
        } else if (arrType.type == CodeConstants.TYPE_FLOAT) {
            defaultVal = new ConstExprent(VarType.VARTYPE_FLOAT, 0f, null);
        } else if (arrType.type == CodeConstants.TYPE_LONG) {
            defaultVal = new ConstExprent(VarType.VARTYPE_LONG, 0L, null);
        } else if (arrType.type == CodeConstants.TYPE_DOUBLE) {
            defaultVal = new ConstExprent(VarType.VARTYPE_DOUBLE, 0d, null);
        } else { // integer types
            defaultVal = new ConstExprent(0, true, null);
        }
        return defaultVal;
    }

    public static boolean getCastedExprent(Exprent exprent,
                                           VarType leftType,
                                           TextBuffer buffer,
                                           int indent,
                                           boolean castNull,
                                           BytecodeMappingTracer tracer) {
        return getCastedExprent(exprent, leftType, buffer, indent, castNull, false, false, tracer);
    }

    public static boolean getCastedExprent(Exprent exprent,
                                           VarType leftType,
                                           TextBuffer buffer,
                                           int indent,
                                           boolean castNull,
                                           boolean castAlways,
                                           boolean castNarrowing,
                                           BytecodeMappingTracer tracer) {
        VarType rightType = exprent.getExprType();

        boolean cast =
                castAlways ||
                        (!leftType.isSuperset(rightType) && (rightType.equals(VarType.VARTYPE_OBJECT) || leftType.type != CodeConstants.TYPE_OBJECT)) ||
                        (castNull && rightType.type == CodeConstants.TYPE_NULL && !UNDEFINED_TYPE_STRING.equals(getTypeName(leftType))) ||
                        (castNarrowing && isIntConstant(exprent) && isNarrowedIntType(leftType));

        boolean quote = cast && exprent.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST);

        // cast instead to 'byte' / 'short' when int constant is used as a value for 'Byte' / 'Short'
        if (castNarrowing && exprent.type == Exprent.EXPRENT_CONST && !((ConstExprent) exprent).isNull()) {
            if (leftType.equals(VarType.VARTYPE_BYTE_OBJ)) {
                leftType = VarType.VARTYPE_BYTE;
            } else if (leftType.equals(VarType.VARTYPE_SHORT_OBJ)) {
                leftType = VarType.VARTYPE_SHORT;
            }
        }

        if (cast) buffer.append('(').append(getCastTypeName(leftType)).append(')');

        if (quote) buffer.append('(');

        if (exprent.type == Exprent.EXPRENT_CONST) {
            ((ConstExprent) exprent).adjustConstType(leftType);
        }

        buffer.append(exprent.toJava(indent, tracer));

        if (quote) buffer.append(')');

        return cast;
    }

    private static boolean isIntConstant(Exprent exprent) {
        if (exprent.type == Exprent.EXPRENT_CONST) {
            switch (((ConstExprent) exprent).getConstType().type) {
                case CodeConstants.TYPE_BYTE:
                case CodeConstants.TYPE_BYTECHAR:
                case CodeConstants.TYPE_SHORT:
                case CodeConstants.TYPE_SHORTCHAR:
                case CodeConstants.TYPE_INT:
                    return true;
            }
        }

        return false;
    }

    private static boolean isNarrowedIntType(VarType type) {
        return VarType.VARTYPE_INT.isStrictSuperset(type) ||
                type.equals(VarType.VARTYPE_BYTE_OBJ) || type.equals(VarType.VARTYPE_SHORT_OBJ);
    }

    public void processStatement(RootStatement root, StructClass cl) {
        FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
        DirectGraph dgraph = flatthelper.buildDirectGraph(root);

        // collect finally entry points
        Set<String> setFinallyShortRangeEntryPoints = new HashSet<>();
        for (List<FinallyPathWrapper> lst : dgraph.mapShortRangeFinallyPaths.values()) {
            for (FinallyPathWrapper finwrap : lst) {
                setFinallyShortRangeEntryPoints.add(finwrap.entry);
            }
        }

        Set<String> setFinallyLongRangeEntryPaths = new HashSet<>();
        for (List<FinallyPathWrapper> lst : dgraph.mapLongRangeFinallyPaths.values()) {
            for (FinallyPathWrapper finwrap : lst) {
                setFinallyLongRangeEntryPaths.add(finwrap.source + "##" + finwrap.entry);
            }
        }

        Map<String, VarExprent> mapCatch = new HashMap<>();
        collectCatchVars(root, flatthelper, mapCatch);

        Map<DirectNode, Map<String, PrimitiveExprsList>> mapData = new HashMap<>();

        LinkedList<DirectNode> stack = new LinkedList<>();
        LinkedList<LinkedList<String>> stackEntryPoint = new LinkedList<>();

        stack.add(dgraph.first);
        stackEntryPoint.add(new LinkedList<String>());

        Map<String, PrimitiveExprsList> map = new HashMap<>();
        map.put(null, new PrimitiveExprsList());
        mapData.put(dgraph.first, map);

        while (!stack.isEmpty()) {

            DirectNode node = stack.removeFirst();
            LinkedList<String> entrypoints = stackEntryPoint.removeFirst();

            PrimitiveExprsList data;
            if (mapCatch.containsKey(node.id)) {
                data = getExpressionData(mapCatch.get(node.id));
            } else {
                data = mapData.get(node).get(buildEntryPointKey(entrypoints));
            }

            BasicBlockStatement block = node.block;
            if (block != null) {
                processBlock(block, data, cl);
                block.setExprents(data.getLstExprents());
            }

            String currentEntrypoint = entrypoints.isEmpty() ? null : entrypoints.getLast();

            for (DirectNode nd : node.succs) {

                boolean isSuccessor = true;
                if (currentEntrypoint != null && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
                    isSuccessor = false;
                    for (FinallyPathWrapper finwraplong : dgraph.mapLongRangeFinallyPaths.get(node.id)) {
                        if (finwraplong.source.equals(currentEntrypoint) && finwraplong.destination.equals(nd.id)) {
                            isSuccessor = true;
                            break;
                        }
                    }
                }

                if (isSuccessor) {
                    Map<String, PrimitiveExprsList> mapSucc = DMap.computeIfAbsent(mapData, nd, new Function<DirectNode, Map<String, PrimitiveExprsList>>() {
                        @Override
                        public Map<String, PrimitiveExprsList> apply(DirectNode k) {
                            return new HashMap<>();
                        }
                    });
                    LinkedList<String> ndentrypoints = new LinkedList<>(entrypoints);

                    if (setFinallyLongRangeEntryPaths.contains(node.id + "##" + nd.id)) {
                        ndentrypoints.addLast(node.id);
                    } else if (!setFinallyShortRangeEntryPoints.contains(nd.id) && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
                        ndentrypoints.removeLast(); // currentEntrypoint should
                        // not be null at this point
                    }

                    // handling of entry point loops
                    int succ_entry_index = ndentrypoints.indexOf(nd.id);
                    if (succ_entry_index >=
                            0) { // we are in a loop (e.g. continue in a finally block), drop all entry points in the list beginning with succ_entry_index
                        for (int elements_to_remove = ndentrypoints.size() - succ_entry_index; elements_to_remove > 0; elements_to_remove--) {
                            ndentrypoints.removeLast();
                        }
                    }

                    String ndentrykey = buildEntryPointKey(ndentrypoints);
                    if (!mapSucc.containsKey(ndentrykey)) {

                        mapSucc.put(ndentrykey, copyVarExprents(data.copyStack()));

                        stack.add(nd);
                        stackEntryPoint.add(ndentrypoints);
                    }
                }
            }
        }

        initStatementExprents(root);
    }

    public void processBlock(BasicBlockStatement stat, PrimitiveExprsList data, StructClass cl) {

        ConstantPool pool = cl.getPool();
        StructBootstrapMethodsAttribute bootstrap =
                (StructBootstrapMethodsAttribute) cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);

        BasicBlock block = stat.getBlock();

        ExprentStack stack = data.getStack();
        List<Exprent> exprlist = data.getLstExprents();

        InstructionSequence seq = block.getSeq();

        for (int i = 0; i < seq.length(); i++) {

            Instruction instr = seq.getInstr(i);
            Integer bytecode_offset = block.getOldOffset(i);
            Set<Integer> bytecode_offsets = bytecode_offset >= 0 ? Collections.singleton(bytecode_offset) : null;

            switch (instr.opcode) {
                case opc_aconst_null:
                    pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_NULL, null, bytecode_offsets));
                    break;
                case opc_bipush:
                case opc_sipush:
                    pushEx(stack, exprlist, new ConstExprent(instr.operand(0), true, bytecode_offsets));
                    break;
                case opc_lconst_0:
                case opc_lconst_1:
                    pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_LONG, (long) (instr.opcode - opc_lconst_0), bytecode_offsets));
                    break;
                case opc_fconst_0:
                case opc_fconst_1:
                case opc_fconst_2:
                    pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_FLOAT, (float) (instr.opcode - opc_fconst_0), bytecode_offsets));
                    break;
                case opc_dconst_0:
                case opc_dconst_1:
                    pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_DOUBLE, (double) (instr.opcode - opc_dconst_0), bytecode_offsets));
                    break;
                case opc_ldc:
                case opc_ldc_w:
                case opc_ldc2_w:
                    PooledConstant cn = pool.getConstant(instr.operand(0));
                    if (cn instanceof PrimitiveConstant) {
                        pushEx(stack, exprlist, new ConstExprent(consts[cn.type - CONSTANT_Integer], ((PrimitiveConstant) cn).value, bytecode_offsets));
                    } else if (cn instanceof LinkConstant) {
                        //TODO: for now treat Links as Strings
                        pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_STRING, ((LinkConstant) cn).elementname, bytecode_offsets));
                    }
                    break;
                case opc_iload:
                case opc_lload:
                case opc_fload:
                case opc_dload:
                case opc_aload:
                    pushEx(stack, exprlist, new VarExprent(instr.operand(0), varTypes[instr.opcode - opc_iload], varProcessor, bytecode_offset));
                    break;
                case opc_iaload:
                case opc_laload:
                case opc_faload:
                case opc_daload:
                case opc_aaload:
                case opc_baload:
                case opc_caload:
                case opc_saload:
                    Exprent index = stack.pop();
                    Exprent arr = stack.pop();

                    VarType vartype = null;
                    switch (instr.opcode) {
                        case opc_laload:
                            vartype = VarType.VARTYPE_LONG;
                            break;
                        case opc_daload:
                            vartype = VarType.VARTYPE_DOUBLE;
                    }
                    pushEx(stack, exprlist, new ArrayExprent(arr, index, arrTypes[instr.opcode - opc_iaload], bytecode_offsets), vartype);
                    break;
                case opc_istore:
                case opc_lstore:
                case opc_fstore:
                case opc_dstore:
                case opc_astore:
                    Exprent expr = stack.pop();
                    int varindex = instr.operand(0);
                    AssignmentExprent assign = new AssignmentExprent(
                            new VarExprent(varindex, varTypes[instr.opcode - opc_istore], varProcessor, nextMeaningfulOffset(block, i)), expr, bytecode_offsets);
                    exprlist.add(assign);
                    break;
                case opc_iastore:
                case opc_lastore:
                case opc_fastore:
                case opc_dastore:
                case opc_aastore:
                case opc_bastore:
                case opc_castore:
                case opc_sastore:
                    Exprent value = stack.pop();
                    Exprent index_store = stack.pop();
                    Exprent arr_store = stack.pop();
                    AssignmentExprent arrassign =
                            new AssignmentExprent(new ArrayExprent(arr_store, index_store, arrTypes[instr.opcode - opc_iastore], bytecode_offsets), value,
                                    bytecode_offsets);
                    exprlist.add(arrassign);
                    break;
                case opc_iadd:
                case opc_ladd:
                case opc_fadd:
                case opc_dadd:
                case opc_isub:
                case opc_lsub:
                case opc_fsub:
                case opc_dsub:
                case opc_imul:
                case opc_lmul:
                case opc_fmul:
                case opc_dmul:
                case opc_idiv:
                case opc_ldiv:
                case opc_fdiv:
                case opc_ddiv:
                case opc_irem:
                case opc_lrem:
                case opc_frem:
                case opc_drem:
                    pushEx(stack, exprlist, new FunctionExprent(func1[(instr.opcode - opc_iadd) / 4], stack, bytecode_offsets));
                    break;
                case opc_ishl:
                case opc_lshl:
                case opc_ishr:
                case opc_lshr:
                case opc_iushr:
                case opc_lushr:
                case opc_iand:
                case opc_land:
                case opc_ior:
                case opc_lor:
                case opc_ixor:
                case opc_lxor:
                    pushEx(stack, exprlist, new FunctionExprent(func2[(instr.opcode - opc_ishl) / 2], stack, bytecode_offsets));
                    break;
                case opc_ineg:
                case opc_lneg:
                case opc_fneg:
                case opc_dneg:
                    pushEx(stack, exprlist, new FunctionExprent(FunctionExprent.FUNCTION_NEG, stack, bytecode_offsets));
                    break;
                case opc_iinc:
                    VarExprent vevar = new VarExprent(instr.operand(0), VarType.VARTYPE_INT, varProcessor);
                    exprlist.add(new AssignmentExprent(vevar, new FunctionExprent(
                            instr.operand(1) < 0 ? FunctionExprent.FUNCTION_SUB : FunctionExprent.FUNCTION_ADD, Arrays
                            .asList(vevar.copy(), new ConstExprent(VarType.VARTYPE_INT, Math.abs(instr.operand(1)), null)),
                            bytecode_offsets), bytecode_offsets));
                    break;
                case opc_i2l:
                case opc_i2f:
                case opc_i2d:
                case opc_l2i:
                case opc_l2f:
                case opc_l2d:
                case opc_f2i:
                case opc_f2l:
                case opc_f2d:
                case opc_d2i:
                case opc_d2l:
                case opc_d2f:
                case opc_i2b:
                case opc_i2c:
                case opc_i2s:
                    pushEx(stack, exprlist, new FunctionExprent(func3[instr.opcode - opc_i2l], stack, bytecode_offsets));
                    break;
                case opc_lcmp:
                case opc_fcmpl:
                case opc_fcmpg:
                case opc_dcmpl:
                case opc_dcmpg:
                    pushEx(stack, exprlist, new FunctionExprent(func4[instr.opcode - opc_lcmp], stack, bytecode_offsets));
                    break;
                case opc_ifeq:
                case opc_ifne:
                case opc_iflt:
                case opc_ifge:
                case opc_ifgt:
                case opc_ifle:
                    exprlist.add(new IfExprent(negIfs[func5[instr.opcode - opc_ifeq]], stack, bytecode_offsets));
                    break;
                case opc_if_icmpeq:
                case opc_if_icmpne:
                case opc_if_icmplt:
                case opc_if_icmpge:
                case opc_if_icmpgt:
                case opc_if_icmple:
                case opc_if_acmpeq:
                case opc_if_acmpne:
                    exprlist.add(new IfExprent(negIfs[func6[instr.opcode - opc_if_icmpeq]], stack, bytecode_offsets));
                    break;
                case opc_ifnull:
                case opc_ifnonnull:
                    exprlist.add(new IfExprent(negIfs[func7[instr.opcode - opc_ifnull]], stack, bytecode_offsets));
                    break;
                case opc_tableswitch:
                case opc_lookupswitch:
                    exprlist.add(new SwitchExprent(stack.pop(), bytecode_offsets));
                    break;
                case opc_ireturn:
                case opc_lreturn:
                case opc_freturn:
                case opc_dreturn:
                case opc_areturn:
                case opc_return:
                case opc_athrow:
                    exprlist.add(new ExitExprent(instr.opcode == opc_athrow ? ExitExprent.EXIT_THROW : ExitExprent.EXIT_RETURN,
                            instr.opcode == opc_return ? null : stack.pop(),
                            instr.opcode == opc_athrow ? null : methodDescriptor.ret,
                            bytecode_offsets));
                    break;
                case opc_monitorenter:
                case opc_monitorexit:
                    exprlist.add(new MonitorExprent(func8[instr.opcode - opc_monitorenter], stack.pop(), bytecode_offsets));
                    break;
                case opc_checkcast:
                case opc_instanceof:
                    stack.push(new ConstExprent(new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true), null, null));
                case opc_arraylength:
                    pushEx(stack, exprlist, new FunctionExprent(mapConsts.get(instr.opcode), stack, bytecode_offsets));
                    break;
                case opc_getstatic:
                case opc_getfield:
                    pushEx(stack, exprlist,
                            new FieldExprent(pool.getLinkConstant(instr.operand(0)), instr.opcode == opc_getstatic ? null : stack.pop(),
                                    bytecode_offsets));
                    break;
                case opc_putstatic:
                case opc_putfield:
                    Exprent valfield = stack.pop();
                    Exprent exprfield =
                            new FieldExprent(pool.getLinkConstant(instr.operand(0)), instr.opcode == opc_putstatic ? null : stack.pop(),
                                    bytecode_offsets);
                    exprlist.add(new AssignmentExprent(exprfield, valfield, bytecode_offsets));
                    break;
                case opc_invokevirtual:
                case opc_invokespecial:
                case opc_invokestatic:
                case opc_invokeinterface:
                case opc_invokedynamic:
                    if (instr.opcode != opc_invokedynamic || instr.bytecodeVersion >= CodeConstants.BYTECODE_JAVA_7) {
                        LinkConstant invoke_constant = pool.getLinkConstant(instr.operand(0));

                        List<PooledConstant> bootstrap_arguments = null;
                        if (instr.opcode == opc_invokedynamic && bootstrap != null) {
                            bootstrap_arguments = bootstrap.getMethodArguments(invoke_constant.index1);
                        }

                        InvocationExprent exprinv = new InvocationExprent(instr.opcode, invoke_constant, bootstrap_arguments, stack, bytecode_offsets);
                        if (exprinv.getDescriptor().ret.type == CodeConstants.TYPE_VOID) {
                            exprlist.add(exprinv);
                        } else {
                            pushEx(stack, exprlist, exprinv);
                        }
                    }
                    break;
                case opc_new:
                case opc_anewarray:
                case opc_multianewarray:
                    int dimensions = (instr.opcode == opc_new) ? 0 : (instr.opcode == opc_anewarray) ? 1 : instr.operand(1);
                    VarType arrType = new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true);
                    if (instr.opcode != opc_multianewarray) {
                        arrType = arrType.resizeArrayDim(arrType.arrayDim + dimensions);
                    }
                    pushEx(stack, exprlist, new NewExprent(arrType, stack, dimensions, bytecode_offsets));
                    break;
                case opc_newarray:
                    pushEx(stack, exprlist, new NewExprent(new VarType(arrTypeIds[instr.operand(0) - 4], 1), stack, 1, bytecode_offsets));
                    break;
                case opc_dup:
                    pushEx(stack, exprlist, stack.getByOffset(-1).copy());
                    break;
                case opc_dup_x1:
                    insertByOffsetEx(-2, stack, exprlist, -1);
                    break;
                case opc_dup_x2:
                    if (stack.getByOffset(-2).getExprType().stackSize == 2) {
                        insertByOffsetEx(-2, stack, exprlist, -1);
                    } else {
                        insertByOffsetEx(-3, stack, exprlist, -1);
                    }
                    break;
                case opc_dup2:
                    if (stack.getByOffset(-1).getExprType().stackSize == 2) {
                        pushEx(stack, exprlist, stack.getByOffset(-1).copy());
                    } else {
                        pushEx(stack, exprlist, stack.getByOffset(-2).copy());
                        pushEx(stack, exprlist, stack.getByOffset(-2).copy());
                    }
                    break;
                case opc_dup2_x1:
                    if (stack.getByOffset(-1).getExprType().stackSize == 2) {
                        insertByOffsetEx(-2, stack, exprlist, -1);
                    } else {
                        insertByOffsetEx(-3, stack, exprlist, -2);
                        insertByOffsetEx(-3, stack, exprlist, -1);
                    }
                    break;
                case opc_dup2_x2:
                    if (stack.getByOffset(-1).getExprType().stackSize == 2) {
                        if (stack.getByOffset(-2).getExprType().stackSize == 2) {
                            insertByOffsetEx(-2, stack, exprlist, -1);
                        } else {
                            insertByOffsetEx(-3, stack, exprlist, -1);
                        }
                    } else {
                        if (stack.getByOffset(-3).getExprType().stackSize == 2) {
                            insertByOffsetEx(-3, stack, exprlist, -2);
                            insertByOffsetEx(-3, stack, exprlist, -1);
                        } else {
                            insertByOffsetEx(-4, stack, exprlist, -2);
                            insertByOffsetEx(-4, stack, exprlist, -1);
                        }
                    }
                    break;
                case opc_swap:
                    insertByOffsetEx(-2, stack, exprlist, -1);
                    stack.pop();
                    break;
                case opc_pop:
                    stack.pop();
                    break;
                case opc_pop2:
                    if (stack.getByOffset(-1).getExprType().stackSize == 1) {
                        // Since value at the top of the stack is a value of category 1 (JVMS9 2.11.1)
                        // we should remove one more item from the stack.
                        // See JVMS9 pop2 chapter.
                        stack.pop();
                    }
                    stack.pop();
                    break;
            }
        }
    }

    private void pushEx(ExprentStack stack, List<Exprent> exprlist, Exprent exprent) {
        pushEx(stack, exprlist, exprent, null);
    }

    private void pushEx(ExprentStack stack, List<Exprent> exprlist, Exprent exprent, VarType vartype) {
        int varindex = VarExprent.STACK_BASE + stack.size();
        VarExprent var = new VarExprent(varindex, vartype == null ? exprent.getExprType() : vartype, varProcessor);
        var.setStack(true);

        exprlist.add(new AssignmentExprent(var, exprent, null));
        stack.push(var.copy());
    }

    private void insertByOffsetEx(int offset, ExprentStack stack, List<Exprent> exprlist, int copyoffset) {

        int base = VarExprent.STACK_BASE + stack.size();

        LinkedList<VarExprent> lst = new LinkedList<>();

        for (int i = -1; i >= offset; i--) {
            Exprent varex = stack.pop();
            VarExprent varnew = new VarExprent(base + i + 1, varex.getExprType(), varProcessor);
            varnew.setStack(true);
            exprlist.add(new AssignmentExprent(varnew, varex, null));
            lst.add(0, (VarExprent) varnew.copy());
        }

        Exprent exprent = lst.get(lst.size() + copyoffset).copy();
        VarExprent var = new VarExprent(base + offset, exprent.getExprType(), varProcessor);
        var.setStack(true);
        exprlist.add(new AssignmentExprent(var, exprent, null));
        lst.add(0, (VarExprent) var.copy());

        for (VarExprent expr : lst) {
            stack.push(expr);
        }
    }
}