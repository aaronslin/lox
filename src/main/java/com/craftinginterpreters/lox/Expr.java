package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Expr extends Lexeme {
  abstract public Object evaluateWith(Visitor<Object> visitor);

  interface Visitor<T> { 
    // not sure why these methods need to be defined
    public T evalEmptyExpr(Empty empty);
    public T evalBinaryExpr(Binary binary);
    public T evalUnaryExpr(Unary unary);
    public T evalGroupingExpr(Grouping grouping);
    public T evalLiteralExpr(Literal literal);
  };
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
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalEmptyExpr(this);
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

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalBinaryExpr(this);
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
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalUnaryExpr(this);
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
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalGroupingExpr(this);
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

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalLiteralExpr(this);
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
