package org.quiltmc.quiltflower.kotlin.expr;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.Typed;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class KFunctionExprent extends FunctionExprent {
  private KFunctionType kType = KFunctionType.NONE;

  public enum KFunctionType implements Typed {
    NONE,

    EQUALS3
  }

  public KFunctionExprent(FunctionType funcType, List<Exprent> operands, BitSet bytecodeOffsets) {
    super(funcType, new ArrayList<>(operands), bytecodeOffsets);
  }

  public KFunctionExprent(FunctionExprent func) {
    super(func.getFuncType(), func.getLstOperands(), func.bytecode);

    setImplicitType(func.getExprType());
    setNeedsCast(func.doesCast());

    if (getFuncType() == FunctionType.EQ) {
      setFuncType(KFunctionType.EQUALS3);
    }
  }

  @Override
  public TextBuffer toJava(int indent) {
  
    TextBuffer buf = new TextBuffer();
    buf.addBytecodeMapping(this.bytecode);
    List<Exprent> lstOperands = getLstOperands();

    optimizeType();
  
    switch(getFuncType()) {
      case OTHER:
        switch (kType) {
          case EQUALS3:
            buf.append(wrapOperandString(lstOperands.get(0), true, indent))
              .append(" === ")
              .append(wrapOperandString(lstOperands.get(1), true, indent));
            return buf;
        }

        throw new IllegalStateException("Unknown function type: " + kType);
      case TERNARY:
        Exprent condition = lstOperands.get(0);
        Exprent ifTrue = lstOperands.get(1);
        Exprent ifFalse = lstOperands.get(2);

        if (
          condition instanceof KFunctionExprent && ((KFunctionExprent) condition).getFuncType() == FunctionType.INSTANCEOF
          && ifTrue instanceof KFunctionExprent && ((KFunctionExprent) ifTrue).getFuncType() == FunctionType.CAST
          && ifFalse.getExprType() == VarType.VARTYPE_NULL
        ) {
          // Safe cast
          KFunctionExprent cast = (KFunctionExprent) ifTrue;
          buf.append(cast.getLstOperands().get(0).toJava(indent));
          buf.append(" as? ");
          buf.append(cast.getLstOperands().get(1).toJava(indent));
          return buf;
        }
      
        buf.pushNewlineGroup(indent, 1);
        buf.append("if (");
        buf.append(wrapOperandString(condition, true, indent))
          .append(")")
          .appendPossibleNewline(" ")
          .append(wrapOperandString(ifTrue, true, indent))
          .appendPossibleNewline(" ")
          .append("else")
          .appendPossibleNewline(" ")
          .append(wrapOperandString(ifFalse, true, indent));
        buf.popNewlineGroup();
      
        return buf;
      case INSTANCEOF:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent))
          .append(" is ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        
        return buf;
      case BOOL_NOT:
        // Special cases for `is` and `!is`
        // TODO: do the same for `in` and `!in`
        if (lstOperands.get(0) instanceof KFunctionExprent) {
          KFunctionExprent func = (KFunctionExprent) lstOperands.get(0);
          if (func.getFuncType() == FunctionExprent.FunctionType.INSTANCEOF) {
            buf.append(wrapOperandString(func.getLstOperands().get(0), true, indent))
              .append(" !is ")
              .append(wrapOperandString(func.getLstOperands().get(1), true, indent));
            return buf;
          }
        }
        break;
      case CAST:
        if (!doesCast()) {
          return buf.append(lstOperands.get(0).toJava(indent));
        }
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" as ").append(lstOperands.get(1).toJava(indent));
        return buf;
      case BIT_NOT:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent));
        return buf.append(".inv()");
      case AND: // Bitwise AND
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" and ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
      case OR:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" or ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
      case XOR:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" xor ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
      case SHL:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" shl ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
      case SHR:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" shr ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
      case USHR:
        buf.append(wrapOperandString(lstOperands.get(0), true, indent)).append(" ushr ")
          .append(wrapOperandString(lstOperands.get(1), true, indent));
        return buf;
    }

    return buf.append(super.toJava(indent));
  }

  @Override
  public VarType getExprType() {
    switch (kType) {
      case EQUALS3:
        return VarType.VARTYPE_BOOLEAN;
    }

    return super.getExprType();
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    Exprent param1 = getLstOperands().get(0);
    VarType type1 = param1.getExprType();
    Exprent param2 = null;
    VarType type2 = null;

    if (getLstOperands().size() > 1) {
      param2 = getLstOperands().get(1);
      type2 = param2.getExprType();
    }

    switch (kType) {
      case EQUALS3: {
        if (type1.type == CodeConstants.TYPE_BOOLEAN) {
          if (type2.isStrictSuperset(type1)) {
            result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
          }
          else { // both are booleans
            boolean param1_false_boolean =
              type1.isFalseBoolean() || (param1 instanceof ConstExprent && !((ConstExprent)param1).hasBooleanValue());
            boolean param2_false_boolean =
              type1.isFalseBoolean() || (param2 instanceof ConstExprent && !((ConstExprent)param2).hasBooleanValue());

            if (param1_false_boolean || param2_false_boolean) {
              result.addMinTypeExprent(param1, VarType.VARTYPE_BYTECHAR);
              result.addMinTypeExprent(param2, VarType.VARTYPE_BYTECHAR);
            }
          }
        }
        else if (type2.type == CodeConstants.TYPE_BOOLEAN) {
          if (type1.isStrictSuperset(type2)) {
            result.addMinTypeExprent(param2, VarType.VARTYPE_BYTECHAR);
          }
        }
      }
    }

    return super.checkExprTypeBounds();
  }

  private void optimizeType() {
    if (getAnyFunctionType() == KFunctionType.EQUALS3) {
      Exprent l = getLstOperands().get(0);
      Exprent r = getLstOperands().get(1);

      if (l.getExprType().typeFamily != CodeConstants.TYPE_FAMILY_OBJECT || r.getExprType().typeFamily != CodeConstants.TYPE_FAMILY_OBJECT) {
        setFuncType(FunctionType.EQ);
      }
    }
  }

  public Typed getAnyFunctionType() {
    FunctionType funcType = getFuncType();

    if (funcType == FunctionType.OTHER) {
      if (kType == KFunctionType.NONE) {
        throw new IllegalStateException("No function type at all set!");
      }

      return kType;
    }

    return funcType;
  }

  @Override
  public void setFuncType(FunctionType funcType) {
    // Forward to the implementation below
    setFuncType((Typed) funcType);
  }

  public void setFuncType(Typed typed) {
    if (typed instanceof FunctionType) {
      // Set only regular func type and remove kotlin type
      super.setFuncType((FunctionType) typed);
      kType = KFunctionType.NONE;
    } else if (typed instanceof KFunctionType) {
      // Set only kotlin func type and remove regular type
      super.setFuncType(FunctionType.OTHER);
      kType = (KFunctionType) typed;
    } else {
      throw new IllegalArgumentException("Unknown function type: " + typed);
    }
  }

  @Override
  public Exprent copy() {
    return new KFunctionExprent((FunctionExprent) super.copy());
  }
}
