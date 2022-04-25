package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Expr extends Lexeme {
  // abstract public Object interpret();
}

class Empty extends Expr {
  Empty() {
    this.children = Arrays.asList();
  }

  @Override
  public String toString() {
    return "[]";
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

  // @Override
  // public Object interpret() {
  //   
  // }
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

  // @Override
  // public Object interpret() {
  //   Object exprValue = expr.interpret();
  //   if (exprValue instanceof Boolean && operator.type == TokenType.BANG) {
  //     return !exprValue;
  //   } else if (exprValue instanceof Double && operator.type == TokenType.MINUS) {
  //     return -exprValue;
  //   } else {
  //     throw new InterpreterError("something");
  //   }
  // }
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

  // @Override
  // public Object interpret() {
  //   return expr.interpret();
  // }
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

  // // (alin) test this! What are the potential types that can be returned here?
  // @Override
  // public Object interpret() {
  //   return token.literal;
  // }
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
