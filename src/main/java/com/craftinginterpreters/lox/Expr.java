package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Expr extends Lexeme {
  abstract public Object evaluateWith(Visitor<Object> visitor);

  interface Visitor<T> { 
    // not sure why these methods need to be defined
    public T evalAssignExpr(Assign assign);
    public T evalBinaryExpr(Binary binary);
    public T evalEmptyExpr(Empty empty);
    public T evalGroupingExpr(Grouping grouping);
    public T evalLiteralExpr(Literal literal);
    public T evalLogicalExpr(Logical literal);
    public T evalUnaryExpr(Unary unary);
    public T evalVarExpr(Var variable);
  };
}

class Empty extends Expr {
  Empty() {
    this._printables = Arrays.asList();
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
    this._printables = Arrays.asList(left, operator, right);
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

class Logical extends Expr {
  Logical(Expr left, Token operator, Expr right) {
    this.left = left;
    this.operator = operator;
    this.right = right;
    this._printables = Arrays.asList(left, operator, right);
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
    return visitor.evalLogicalExpr(this);
  }
}

class Unary extends Expr {
  Unary(Token operator, Expr expr) {
    this.operator = operator;
    this.expr = expr;
    this._printables = Arrays.asList(operator, expr);
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
  Grouping(Expr expr) {
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  @Override
  public String toString() {
    return "" + expr;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalGroupingExpr(this);
  }
}

class Literal extends Expr {
  Literal(Object value) {
    this.value = value;
    this._printables = Arrays.asList();
  }

  final Object value;

  @Override
  public String toString() {
    return "" + value;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalLiteralExpr(this);
  }
}

class Var extends Expr {
  Var(Token name) {
    this.name = name;
    this._printables = Arrays.asList();
  }

  final Token name;

  @Override
  public String toString() {
    return "" + name;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalVarExpr(this);
  }
}

class Assign extends Expr {
  Assign(Token name, Expr expr) {
    this.name = name;
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Token name;
  final Expr expr;

  @Override
  public String toString() {
    return "" + name + expr;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalAssignExpr(this);
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
