package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

/* 
program -> statement* EOF
statement -> { statement* }

statement -> expression ;
statement -> PRINT expression ;
statement -> (var)? IDENT (= expression)?;
*/

public class StatementParser {
  StatementParser(List<Token> tokens) {
    this.index = 0;
    this.tokens = tokens;
  }

  int index;
  List<Token> tokens;

  public Void until(TokenType terminalType) {
    while (index < tokens.size()) {
      // check that the increment and condition order is correct
      if (tokens.get(index).type == terminalType) {
        return null;
      }
      index += 1;
    }
    throw new ParserException(String.format("Reached EOF without finding %s token.", terminalType));
  }

  public List<Statement> parseProgram() {
    List<Statement> statements = new ArrayList<>();
    
    while (index < tokens.size()) {
      if (tokens.get(index).type == TokenType.EOF) {
        break;
      }
      statements.add(parseBlock());
    }
    return statements;
  }

  private Statement parseBlock() {

    if (tokens.get(index).type == TokenType.LEFT_BRACE) {
      int start = index;
      until(TokenType.RIGHT_BRACE);
      StatementParser parser = new StatementParser(tokens.subList(start+1, index));
      BlockStmt block = new BlockStmt(parser.parseProgram());

      index += 1;
      return block;
    }

    return parseLine();
  }

  private Statement parseLine() {
    int start = index;
    until(TokenType.SEMICOLON);
    return parseStmt(tokens.subList(start, index));
  }

  private static Statement parseStmt(List<Token> tokens) {
    if (tokens.size() > 0 && tokens.get(0).type == TokenType.PRINT) {
      return new PrintStmt(Parser.parseExpr(tokens.subList(1, tokens.size())));
    } else if (tokens.size() > 1
            && tokens.get(0).type == TokenType.VAR
            && tokens.get(1).type == TokenType.IDENTIFIER) {
      List<Token> rhs = tokens.subList(2, tokens.size());

      if (rhs.size() == 0) {
        return _parseAssign(tokens.get(1), new Empty());
      } else if (rhs.get(0).type == TokenType.EQUAL) {
        return _parseAssign(tokens.get(1), rhs.subList(1, rhs.size()));
      }
      throw new ParserException(String.format(
        "Assignment must contain an '=' followed by expression, but found %s.", 
        tokens
      ));
    } else if (tokens.size() > 1
            && tokens.get(0).type == TokenType.IDENTIFIER
            && tokens.get(1).type == TokenType.EQUAL) {
      return _parseAssign(tokens.get(0), tokens.subList(2, tokens.size()));
    }
    return new ExprStmt(Parser.parseExpr(tokens));
  }

  private static Statement _parseAssign(Token varName, List<Token> tokens) {
    if (tokens.size() == 0) {
      throw new ParserException(String.format("Cannot assign variable '%s' to an empty expression.", varName));
    }
    return _parseAssign(varName, Parser.parseExpr(tokens));
  }

  private static Statement _parseAssign(Token varName, Expr expr) {
    return new AssignStmt(
      varName.literal.toString(), 
      new Variable(expr)
    );
  }
}
