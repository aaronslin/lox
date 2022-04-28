package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Expr extends Lexeme {
  abstract public Object evaluate();

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
}

class Empty extends Expr {
  Empty() {
    this.children = Arrays.asList();
  }

  @Override
  public String toString() {
    return "[]";
  }

  @Override
  public Object evaluate() {
    return null;
  }
}


class Binary extends Expr {
  Binary(Expr left, Token operator, Expr right) {
    /* Binary operator expressions. e.g. 1+2 */
    this.left = left;
    this.operator = operator;
    this.right = right;
    this.children = Arrays.asList(left, operator, right);
  }

  final Expr left;
  final Token operator;
  final Expr right;

  @Override
  public String toString() {
    return "" + left + operator + right;
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
  public Object evaluate() {
    Object leftVal = left.evaluate();
    Object rightVal = right.evaluate();
    
    try {
      switch(operator.type) {
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
          throw new InterpreterException(String.format("Operator %s is not supported.", operator.type));
      }
    } catch (InterpreterCastException e) {
      throw new InterpreterException(String.format(
        "Binary operator %s cannot be applied to expression %s of type %s.", 
        operator.lexeme, 
        e.obj,
        e.obj.getClass().getName()
      ));
    }
    // Cases: String, Nil, Number, Boolean
    // Equality: ???
    // Comparison: check type equality?
    // Mult/Add: return bool if both_bool else double?
  }
}

class Unary extends Expr {
  Unary(Token operator, Expr expr) {
    this.operator = operator;
    this.expr = expr;
    this.children = Arrays.asList(operator, expr);
  }

  final Token operator;
  final Expr expr;

  @Override
  public String toString() {
    return "" + operator + expr;
  }

  @Override
  public Object evaluate() {
    Object exprValue = expr.evaluate();
    try {
      if (operator.type == TokenType.BANG) {
        return !toBool(exprValue);
      } else if (operator.type == TokenType.MINUS) {
        return -toNum(exprValue);
      } else {
        throw new InterpreterException(String.format(
          "Unary operator %s not supported for expression %s of type %s.",
          operator.lexeme,
          exprValue,
          exprValue.getClass().getName()
        ));
      }
    } catch (InterpreterCastException e) {
      throw new InterpreterException(String.format(
        "Unary operator %s cannot be applied to expression %s of type %s.", 
        operator.lexeme, 
        e.obj,
        e.obj.getClass().getName()
      ));
    }
  }
}

class Grouping extends Expr {
  Grouping(Token left, Expr expr, Token right) {
    this.left = left;
    this.expr = expr;
    this.right = right;
    this.children = Arrays.asList(left, expr, right);
  }

  final Token left;
  final Expr expr;
  final Token right;

  @Override
  public String toString() {
    return "" + left + expr + right;
  }

  @Override
  public Object evaluate() {
    return expr.evaluate();
  }
}

class Literal extends Expr {
  Literal(Token token) {
    this.token = token;
    this.children = Arrays.asList(token);
  }

  final Token token;

  @Override
  public String toString() {
    return "" + token;
  }

  // (alin) test this! What are the potential types that can be returned here?
  @Override
  public Object evaluate() {
    return token.literal;
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
Goal: For numerical/boolean expressions, return all satisfying trees

expression -> literal | grouping | binary | unary

grouping -> LEFT_PAREN expression RIGHT_PAREN
unary -> (MINUS | BANG) expression
binary -> expression INFIX_OP expression 
literal -> NUMBER | BOOLEAN

BOOLEAN -> TRUE FALSE
INFIX_OP -> + - * / != == < <= > >= AND OR

1 - (2 * 3) < 4 == false
*/
