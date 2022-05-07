package com.craftinginterpreters.lox;

class Interpreter implements Expr.Visitor<Object>,
                             Statement.Visitor<Void> {
  Interpreter() {
    this.currentScope = new Scope(null);
  }

  Scope currentScope;

  void enterScope() {
    Scope innerScope = new Scope(currentScope);
    currentScope = innerScope;
  }

  void exitScope() {
    Scope outerScope = currentScope.parent;
    if (outerScope == null) {
      throw new InterpreterException("Current scope is outermost and cannot be exited.");
    }
    // No cleanup needed -- outerScope variables should never reference inner scope vars
    currentScope = outerScope;
  }

  private Object evaluate(Expr expr) {
    return expr.evaluateWith(this);
  }

  public void execute(Statement stmt) {
    stmt.executeWith(this);
  }

  static double toNum(Object obj) {
    if (obj == null) {
      return 0.0;
    } else if (obj instanceof Double) {
      return (double) obj;
    } else if (obj instanceof Boolean) {
      return ((boolean) obj ? 1.0 : 0.0);
    } else {
      throw new InterpreterCastException("Cannot cast to double.", obj);
    }
  }

  static boolean toBool(Object obj) {
    if (obj == null) {
      return false;
    } else if (obj instanceof Double) {
      return ((double) obj) != 0;
    } else if (obj instanceof Boolean) {
      return (boolean) obj;
    } else {
      throw new InterpreterCastException("Cannot cast to boolean.", obj);
    }
  }
  
  static Object _add(Object leftObj, Object rightObj) {
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

        // Supported: (bool, bool)
        case AND:
          return toBool(leftVal) && toBool(rightVal);
        case OR: 
          return toBool(leftVal) || toBool(rightVal);

        // Supported: everything. Note that 1!=true
        case BANG_EQUAL: 
          return !_equals(leftVal, rightVal);
        case EQUAL_EQUAL: 
          return _equals(leftVal, rightVal);
        default:
          throw new InterpreterException(String.format("Operator %s is not supported.", binary.operator.type));
      }
    } catch (InterpreterCastException e) {
      throw new InterpreterException(String.format(
        "Binary operator %s cannot be applied to expression %s of type %s.", 
        binary.operator.lexeme, 
        e.obj,
        e.obj.getClass().getName()
      ));
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
        throw new InterpreterException(String.format(
          "Unary operator %s not supported for expression %s of type %s.",
          unary.operator.lexeme,
          exprValue,
          exprValue.getClass().getName()
        ));
      }
    } catch (InterpreterCastException e) {
      throw new InterpreterException(String.format(
        "Unary operator %s cannot be applied to expression %s of type %s.", 
        unary.operator.lexeme, 
        e.obj,
        e.obj.getClass().getName()
      ));
    }
  }

  @Override
  public Object evalGroupingExpr(Grouping grouping) {
    return evaluate(grouping.expr);
  }

  @Override
  public Object evalLiteralExpr(Literal literal) {
    if (literal.token.type == TokenType.IDENTIFIER) {
      Variable variable = currentScope.get(literal.token.literal.toString());
      return evaluate(variable.expr);
    }
    return literal.token.literal;
  }

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
  public Void execAssignStmt(AssignStmt stmt) {
    currentScope.set(stmt.name, stmt.variable);
    return null;
  }

  @Override
  public Void execBlockStmt(BlockStmt stmt) {
    // todo: do something with new scope
    enterScope();
    for (Statement substmt : stmt.statements) {
      execute(substmt);
    }
    exitScope();
    return null;
  }
}

class InterpreterCastException extends RuntimeException {
  Object obj;

  public InterpreterCastException(String message, Object obj) {
    super(message);
    this.obj = obj;
  }
}

class InterpreterException extends RuntimeException {
  String code;
  int column;

  public InterpreterException(String message, String code, int column) {
    super(message);
    this.code = code;
    this.column = column;
  }

  public InterpreterException(String message) {
    this(message, "", -1);
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
