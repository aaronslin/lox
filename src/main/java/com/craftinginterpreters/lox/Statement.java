package com.craftinginterpreters.lox;

import java.util.Arrays;

abstract class Statement extends Printable {
  abstract public void execute();
}

class ExprStmt extends Statement {
  ExprStmt(Expr expr) {
    this.expr = expr;
    this.children = Arrays.asList(expr);
  }

  final Expr expr;

  public void execute() {
    expr.evaluate();
  }
}

class PrintStmt extends Statement {
  PrintStmt(Expr expr) {
    this.expr = expr;
    this.children = Arrays.asList(expr);
  }

  final Expr expr;

  public void execute() {
    System.out.println(expr.evaluate());
  }
}

/*
program        → statement* EOF ;

statement      → exprStmt
               | printStmt ;

exprStmt       → expression ";" ;
printStmt      → "print" expression ";" ;
*/
