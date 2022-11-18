package org.quiltmc.quiltflower.kotlin.pass;

import org.jetbrains.java.decompiler.api.passes.Pass;
import org.jetbrains.java.decompiler.api.passes.PassContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.quiltmc.quiltflower.kotlin.expr.KFunctionExprent;
import org.quiltmc.quiltflower.kotlin.expr.KVarExprent;

import java.util.Iterator;
import java.util.List;

public class ReplaceExprentsPass implements Pass {
  @Override
  public boolean run(PassContext ctx) {
    return replace(ctx.getRoot());
  }

  private static boolean replace(Statement stat) {
    boolean res = false;

    for (Statement st : stat.getStats()) {
      res |= replace(st);
    }

    if (stat instanceof BasicBlockStatement) {
      List<Exprent> exprs = stat.getExprents();

      for (int i = 0; i < exprs.size(); i++) {
        Exprent expr = exprs.get(i);
        Exprent map = map(expr);

        if (map != null) {
          exprs.set(i, map);
          res = true;
        }
      }

      for (Exprent ex : exprs) {
        res |= replace(ex);
      }
    }

    for (int i = 0; i < stat.getVarDefinitions().size(); i++) {
      Exprent expr = stat.getVarDefinitions().get(i);

      Exprent map = map(expr);
      if (map != null) {
        stat.getVarDefinitions().set(i, map);

        res = true;
      }
    }

    return res;
  }

  private static boolean replace(Exprent expr) {
    boolean res = false;

    for (Exprent ex : expr.getAllExprents()) {
      Exprent map = map(ex);

      if (map != null) {
        expr.replaceExprent(ex, map);
      } else {
        res |= replace(ex);
      }
    }

    return res;
  }

  private static Exprent map(Exprent ex) {
    if (ex instanceof FunctionExprent) {
      return new KFunctionExprent((FunctionExprent) ex);
    } else if (ex instanceof VarExprent) {
      return new KVarExprent((VarExprent) ex);
    }

    return null;
  }
}