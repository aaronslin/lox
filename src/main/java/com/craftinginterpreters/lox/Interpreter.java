package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
                             Statement.Visitor<Void> {
  Interpreter() {
    this.currentScope = new Scope(null);
    this.recursionDepth = 0;
  }

  final static int MAX_RECURSION_DEPTH = 10;

  Scope currentScope;
  int recursionDepth;

  void interpret(List<Statement> statements) {
    try {
      for (Statement statement : statements) {
        statement.print();
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  private Object evaluate(Expr expr) {
    return expr.evaluateWith(this);
  }

  public void execute(Statement stmt) {
    stmt.executeWith(this);
  }

  static double toNum(Object obj) throws InterpreterCastException {
    if (obj == null) {
      return 0.0;
    } else if (obj instanceof Double) {
      return (double) obj;
    } else if (obj instanceof Boolean) {
      return ((boolean) obj ? 1.0 : 0.0);
    } else {
      throw new InterpreterCastException("double", obj);
    }
  }

  static boolean toBool(Object obj) {
    if (obj == null) {
      return false;
    } else if (obj instanceof Double) {
      return ((double) obj) != 0;
    } else if (obj instanceof Boolean) {
      return (boolean) obj;
    } else if (obj instanceof String) {
      return !obj.equals("");
    }
    return true;
  }
  
  static Object _add(Object leftObj, Object rightObj) throws InterpreterCastException {
    if (leftObj instanceof String && rightObj instanceof String) {
      return (String) leftObj + (String) rightObj;
    } else {
      return toNum(leftObj) + toNum(rightObj);
    }
  }

  static boolean _equals(Object leftObj, Object rightObj) {
    if (leftObj == null && rightObj == null) {
      return true;
    } else if (leftObj == null || rightObj == null) {
      return false;
    }
    return leftObj.equals(rightObj);
  }

  @Override
  public Object evalEmptyExpr(Empty empty) {
    return null;
  }

  @Override
  public Object evalBinaryExpr(Binary binary) {
    Object leftVal = evaluate(binary.left);
    Object rightVal = evaluate(binary.right);
    
    try {
      switch(binary.operator.type) {
        // Supported: numerical, except PLUS
        case SLASH: 
          return toNum(leftVal) / toNum(rightVal);
        case STAR: 
          return toNum(leftVal) * toNum(rightVal);
        case PLUS: 
          return _add(leftVal, rightVal);
        case MINUS: 
          return toNum(leftVal) - toNum(rightVal);

        // Supported: numerical
        case GREATER_EQUAL:
          return toNum(leftVal) >= toNum(rightVal);
        case LESS_EQUAL: 
          return toNum(leftVal) <= toNum(rightVal);
        case GREATER: 
          return toNum(leftVal) > toNum(rightVal);
        case LESS: 
          return toNum(leftVal) < toNum(rightVal);

        // Supported: everything. Note that 1!=true
        case BANG_EQUAL: 
          return !_equals(leftVal, rightVal);
        case EQUAL_EQUAL: 
          return _equals(leftVal, rightVal);
        default:
          throw new RuntimeError(binary.operator, "Binary operator is not supported.");
      }
    } catch (InterpreterCastException e) {
      throw new RuntimeError(binary.operator, e.getMessage());
    }
  }

  @Override
  public Object evalUnaryExpr(Unary unary) {
    Object exprValue = evaluate(unary.expr);
    try {
      if (unary.operator.type == TokenType.BANG) {
        return !toBool(exprValue);
      } else if (unary.operator.type == TokenType.MINUS) {
        return -toNum(exprValue);
      } else {
        throw new RuntimeError(unary.operator, "Unary operator is not supported.");
      }
    } catch (InterpreterCastException e) {
      throw new RuntimeError(unary.operator, e.getMessage());
    }
  }

  @Override
  public Object evalGroupingExpr(Grouping grouping) {
    return evaluate(grouping.expr);
  }

  @Override
  public Object evalLiteralExpr(Literal literal) {
    return literal.value;
  }

  @Override
  public Object evalLogicalExpr(Logical logical) {
    Object leftVal = evaluate(logical.left);
    if (logical.operator.type == TokenType.AND) {
      if (!toBool(leftVal)) return leftVal;
    } else if (logical.operator.type == TokenType.OR) {
      if (toBool(leftVal)) return leftVal;
    } else {
      throw new RuntimeError(logical.operator, "Logical operator is not supported.");
    }
    return evaluate(logical.right);
  }

  @Override
  public Object evalVarExpr(Var varName) {
    Variable variable = currentScope.get(varName.name);
    return variable.value;
  }

  @Override
  public Object evalAssignExpr(Assign assign) {
    Variable value = new Variable(evaluate(assign.expr));
    currentScope.assign(assign.name, value);
    return value.value;
  }

  // (alin) reason about this in the inefficient fibonacci case, where you could have branching recursion
  // (alin) reason about this in the even/odd recursion case
  // (alin) does f(g)(x,y) work?
  @Override
  public Object evalFuncExpr(Func func) {
    // Check that func points to a funcStmt
    Object result = evaluate(func.callee);
    FuncStmt funcStmt;
    if (!(result instanceof FuncStmt)) {
      throw new RuntimeError(func.identifier, "Expression is not callable.");
    } else {
      funcStmt = (FuncStmt) result;
    }

    // Evaluate arguments in outer scope
    if (func.parameters.size() != funcStmt.parameters.size()) {
      throw new RuntimeError(func.identifier, "Wrong number of arguments given.");
    }
    List<Object> args = evalSeries(func.parameters);

    // Check recursion depth
    if (recursionDepth > MAX_RECURSION_DEPTH) {
      throw new RuntimeError(func.identifier, String.format("Maximum recursion depth reached: %s", MAX_RECURSION_DEPTH));
    }

    // Initialize scope and increment recursionDepth -- these must be reset before returning/throwing!
    recursionDepth++;
    Scope outerScope = currentScope;
    currentScope = new Scope(currentScope);
    try {
      // Initialize function parameters
      for (int i=0; i<args.size(); i++) {
        currentScope.declare(
          funcStmt.parameters.get(i).name, 
          new Variable(args.get(i))
        );
      }

      // Execute function body
      for (Statement stmt : funcStmt.body.statements) {
        // Should there be any special exception handling here?
        execute(stmt);
      }
    } catch (ReturnException e) {
      return e.value;
    } finally {
      currentScope = outerScope;
      recursionDepth--;
    }

    return null;
  }

  public <T extends Expr> List<Object> evalSeries(Series<T> series) {
    List<Object> values = new ArrayList<>();
    for (T item : series.members) { 
      values.add(evaluate(item));
    }
    return values;
  }

  /*
   o-----------------o
   | EXECUTE METHODS |
   o-----------------o
  */

  @Override
  public Void execExprStmt(ExprStmt stmt) {
    evaluate(stmt.expr);
    return null;
  }

  @Override
  public Void execPrintStmt(PrintStmt stmt) {
    System.out.println(evaluate(stmt.expr));
    return null;
  }

  @Override
  public Void execVarStmt(VarStmt stmt) {
    Variable value = new Variable(evaluate(stmt.expr));
    currentScope.declare(stmt.name, value);
    return null;
  }

  @Override
  public Void execBlockStmt(BlockStmt stmt) {
    Scope outerScope = currentScope;
    currentScope = new Scope(currentScope);
    try {
      for (Statement substmt : stmt.statements) {
        execute(substmt);
      }
    } finally {
      currentScope = outerScope;
    }
    return null;
  }

  @Override
  public Void execIfStmt(IfStmt stmt) {
    if (toBool(evaluate(stmt.condition))) {
      execute(stmt.then);
    } else {
      execute(stmt.otherwise);
    }
    return null;
  }

  @Override
  public Void execWhileStmt(WhileStmt stmt) {
    while (toBool(evaluate(stmt.condition))) {
      execute(stmt.body);
    }
    return null;
  }

  @Override
  public Void execForStmt(ForStmt stmt) {
    Scope outerScope = currentScope;
    currentScope = new Scope(currentScope);
    
    try {
      execute(stmt.initializer);
      while (toBool(evaluate(stmt.condition))) {
        if (stmt.body instanceof BlockStmt) {
          BlockStmt body = (BlockStmt) stmt.body;
          for (Statement substmt : body.statements) { 
            execute(substmt);
          }
        } else {
          execute(stmt.body);
        }
        execute(stmt.iterator);
      }
    } finally {
      currentScope = outerScope;
    }

    return null;
  }

  @Override
  public Void execFuncStmt(FuncStmt stmt) {
    currentScope.declare(stmt.name.name, new Variable(stmt));
    return null;
  }

  @Override
  public Void execReturnStmt(ReturnStmt stmt) {
    throw new ReturnException(evaluate(stmt.expr));
  }
}

class ReturnException extends RuntimeException {
  public ReturnException(Object value) {
    this.value = value;
  }

  Object value;
}

class InterpreterCastException extends Exception {
  public InterpreterCastException(String javaType, Object obj) {
    super(String.format("Cannot cast %s (type: %s) to %s.", obj, obj.getClass().getName(), javaType));
  }
}

/* 
Notes:
 - Here I execute an Expr tree. (As opposed to compiling to machine code or other options)
 - Lox is dynamically typed but Java is static!

Corrections:
0 / 0 (returns NaN, but should throw error)

Misleading error messages:
)
5 + "hi"
5 hi ("Cannot end with expression")
---- ("Must start with expression")

Feature differences:
 - the book's interpreter doesn't allow addition on booleans. 
 - the book's interpreter evaluates strings (even "") to true. Mine throws.
 - My interpreter lacks an interpret() method to map Java output values into
   Lox string that is shown to the user.
 - the book's interpreter uses the visitor pattern to write the evaluate()
   methods in the Interpreter, instead of scattering it into the Expr classes.
*/
