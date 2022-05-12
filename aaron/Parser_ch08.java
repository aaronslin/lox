/*
This is my implementation of the parser up until the end of Ch. 8.
I am swapping this out for the book's implementation, which uses a recursive descent parser.
This will make it easier to implement control flow parsing.
*/
package com.craftinginterpreters.lox;

import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Parser {
  final static List<TokenType> LITERAL_TYPES = ImmutableList.of(
    TokenType.NUMBER,
    TokenType.STRING,
    TokenType.TRUE,
    TokenType.FALSE,
    TokenType.NIL
  );

  final static List<TokenType> UNARY_TYPES = ImmutableList.of(
    TokenType.BANG,
    TokenType.MINUS
  );

  // Order of operations
  final static List<List<TokenType>> BINARY_TYPES = ImmutableList.of(
    ImmutableList.of(
      TokenType.SLASH,
      TokenType.STAR
    ),
    ImmutableList.of(
      TokenType.PLUS,
      TokenType.MINUS
    ),
    ImmutableList.of(
      TokenType.GREATER_EQUAL,
      TokenType.LESS_EQUAL,
      TokenType.GREATER,
      TokenType.LESS
    ),
    ImmutableList.of(
      TokenType.BANG_EQUAL,
      TokenType.EQUAL_EQUAL
    ),
    ImmutableList.of(
      TokenType.AND,
      TokenType.OR
    )
  );

  final static boolean isDebug = false;

  int index;
  List<Token> tokens;

  Parser(List<Token> tokens) {
    this.index = 0;
    this.tokens = tokens;
  }

  private static void print(List<Lexeme> lexemes) {
    if (isDebug) {
      String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
      System.out.printf("%s:%n", caller);
      for (Lexeme lexeme : lexemes) {
        System.out.printf("|_ %s%n", lexeme);
      }
    }
  }

  private void _printCurrent() {
    if (isDebug) {
      String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
      System.out.printf("%s:%n", caller);
      for (int i=0; i<tokens.size(); i++) {
        String open = " ";
        String close = " ";
        if (i == index) {
          open = "[";
          close = "]";
        }
        System.out.println("└─" + open + tokens.get(i) + close);
      }
    }
  }

  private Token consume() {
    // Are there Indexoutofbounds errors here?
    Token token = tokens.get(index);
    index += 1;
    return token;
  }

  private boolean match(TokenType type) {
    // Are there Indexoutofbounds errors here?
    if (tokens.get(index).type == type) {
      index += 1;
      return true;
    }
    return false;
  }

  private Void until(TokenType terminalType) {
    /* Find the first `terminalType` token from the current `index` (inclusive)
    and set the `index` to the token after it. */
    while (index < tokens.size()) {
      if (tokens.get(index).type == terminalType) {
        index += 1;
        return null;
      }
      index += 1;
    }
    throw new ParserException(String.format("Reached EOF without finding %s token.", terminalType));
  }

  public List<Statement> parseProgram() {
    _printCurrent();
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
    _printCurrent();
    if (tokens.get(index).type == TokenType.LEFT_BRACE) {
      int start = index;
      until(TokenType.RIGHT_BRACE);
      Parser parser = new Parser(tokens.subList(start+1, index-1));
      return new BlockStmt(parser.parseProgram());
    }

    return parseDeclaration();
  }

  private Statement parseIf() {
    // IF grouping statement
  }

  private Statement parseFor() {
    // FOR LPAREN statement expression; statement RPAREN statement
    
  }

  private Statement parseDeclaration() {
    _printCurrent();
    if (match(TokenType.VAR)) {
      Token token = consume();

      if (token.type != TokenType.IDENTIFIER) {
        throw new ParserException("Keyword 'var' must be followed by an identifier.");
      }
      String name = token.literal.toString();

      if (match(TokenType.EQUAL)) {
        int start = index;
        until(TokenType.SEMICOLON);
        // this will succeed on `var x = ;`
        // but the problem here is due to the existince of Empty()
        // `var x = ();` also passes, and the parser cannot distinguish this
        // so I will simply allow it.
        Expr rhs = parseExpr(tokens.subList(start, index-1));
        return new AssignStmt(name, rhs);
      } else if (match(TokenType.SEMICOLON)) {
        return new AssignStmt(name, new Empty());
      } else {
        throw new ParserException("Invalid assignment initializer.");
      }
    }
    // For now, this parser does not support `a=b`. Only `var a=b`.

    return parsePrint();
  }

  private Statement parsePrint() {
    _printCurrent();
    if (tokens.get(index).type == TokenType.PRINT) {
      int start = index;
      until(TokenType.SEMICOLON);
      return new PrintStmt(parseExpr(tokens.subList(start+1, index-1)));
    }
    return parseExprStmt();
  }

  private Statement parseExprStmt() {
    _printCurrent();
    int start = index;
    until(TokenType.SEMICOLON);
    return new ExprStmt(parseExpr(tokens.subList(start, index-1)));
  }

  public static Expr parseExpr(List<Token> tokens) {
    /* 
    Assumes:
     * No EOF in input.

    1. Parse literals ("nouns").
    2. For each grouping ("clauses"):
       a. Parse unary operators ("adjectives").
       b. Parse binary operators ("verbs").
    */ 
    if (tokens.size() == 0) {
      return new Empty();
    }

    List<Lexeme> lexemes = new ArrayList<>();
    Stack<List<Lexeme>> exprStack = new Stack<>();
    Stack<Token> openParenStack = new Stack<>();

    for (Token token : tokens) {
      // Match literals and cast them to Exprs
      if (LITERAL_TYPES.contains(token.type)) {
        lexemes.add(new Literal(token));
        continue;
      }

      if (token.type == TokenType.IDENTIFIER) {
        lexemes.add(new Var(token.literal.toString()));
        continue;
      }

      // Find matching parenthesis
      if (token.type == TokenType.LEFT_PAREN) {
        exprStack.push(lexemes);
        openParenStack.push(token);
        lexemes = new ArrayList<>();
        continue;
      } else if (token.type == TokenType.RIGHT_PAREN) {
        Expr ungroupedExpr = parseGroup(lexemes);
        // case 1: unparsable... just throw the error
        // case 2: parsed
        try {
          Token openParen = openParenStack.pop();
          lexemes = exprStack.pop();
          Expr expr = new Grouping(openParen, ungroupedExpr, token);
          lexemes.add(expr);
        } catch (EmptyStackException e) {
          throw new ParserException(String.format("Unmatched %s.", token.lexeme));
        }
        continue;
      }

      // Ignore everything else
      lexemes.add(token);
    }

    // Check that the stacks are empty. This could be simplified without having openParenStack.
    if (!openParenStack.empty()) {
      Token token = openParenStack.pop();
      throw new ParserException(String.format("Unmatched %s.", token.lexeme));
    } else if (!exprStack.empty()) {
      lexemes = exprStack.pop();
      throw new ParserException(String.format("Extraneous lexemes: %s.", lexemes));
    }

    print(lexemes);
    return parseGroup(lexemes);
  }

  private static Expr parseGroup(List<Lexeme> unparsed) {
    if (unparsed.size() == 0) {
      throw new ParserException("Empty group is not allowed.");
    }
    List<Lexeme> lexemes = unparsed;

    lexemes = _parseUnary(lexemes);
    lexemes = _parseBinary(lexemes);
    lexemes = _parseAssign(lexemes);

    if (lexemes.size() > 1) {
      throw new ParserException(String.format(
        "Failed to parse input into a single expression. Parsed %s into %s.",
        unparsed,
        lexemes
      ));
    }
    print(lexemes);
    return (Expr) lexemes.get(0);
  }

  private static List<Lexeme> _parseAssign(List<Lexeme> lexemes) {
    if (lexemes.size() < 2) {
      return lexemes;
    }

    if (lexemes.get(0) instanceof Var) {
      Var name = (Var) lexemes.get(0);
      if (lexemes.get(1) instanceof Token) {
        Token token = (Token) lexemes.get(1);
        if (token.type == TokenType.EQUAL) {
          Expr rhs = parseGroup(lexemes.subList(2, lexemes.size()));
          return ImmutableList.of(new Assign(name.name, rhs));
        }
      }
    }
    throw new ParserException(String.format("Cannot parse assignment for %s.", lexemes));
  }

  private static List<Lexeme> _parseBinary(List<Lexeme> lexemes) {
    for (List<TokenType> operations : BINARY_TYPES) {
      lexemes = _parseBinary(lexemes, operations);
    }
    return lexemes;
  }

  private static List<Lexeme> _parseBinary(List<Lexeme> lexemes, List<TokenType> operations) {
    /*
    Assumes:
      * Input is alternates between Expr's and operation Token's, starting and ending with Expr's.
      * All grouping and unary operators have been stripped.
    */
    // Assert lexemes are in [Expr Token Expr Token ... Expr] format.
    int violation = IntStream.range(0, lexemes.size())
      .map(i -> (i%2 == 0 ? lexemes.get(i) instanceof Expr : lexemes.get(i) instanceof Token) ? 1 : 0)
      .boxed()
      .collect(Collectors.toList())
      .indexOf(0);

    if (violation == 0) {
      throw new ParserException(String.format(
        "Group must start with an expression, but found %s.", 
        lexemes.get(0)
      ));
    } else if (violation > 0 && violation % 2 == 0) {
      throw new ParserException(String.format(
        "Cannot parse two adjacent tokens: %s %s", 
        lexemes.get(violation-1),
        lexemes.get(violation)
      ));
    } else if (violation > 0 && violation % 2 == 1) {
      throw new ParserException(String.format(
        "Cannot parse two adjacent expressions: %s %s", 
        lexemes.get(violation-1),
        lexemes.get(violation)
      ));
    } else if (lexemes.size() % 2 == 0) {
      throw new ParserException(String.format(
        "Group must end with an expression, but found %s.", 
        lexemes.get(lexemes.size()-1)
      ));
    }

    // Split `lexemes` into exprs and tokens
    List<Expr> exprs = new ArrayList<>();
    List<Token> tokens = new ArrayList<>();
    for (int i=0; i<lexemes.size(); i++) {
      if (i%2 == 0) {
        exprs.add((Expr) lexemes.get(i));
      } else {
        tokens.add((Token) lexemes.get(i));
      }
    }

    List<Lexeme> parsed = new ArrayList<>();
    for (int i=0; i<tokens.size(); i++) {
      if (operations.contains(tokens.get(i).type)) {
        Binary operated = new Binary(exprs.get(i), tokens.get(i), exprs.get(i+1));
        exprs.set(i+1, operated);
      } else {
        parsed.add(exprs.get(i));
        parsed.add(tokens.get(i));
      }
    }
    parsed.add(exprs.get(exprs.size()-1));

    print(parsed);
    return parsed;
  }

  private static List<Lexeme> _parseUnary(List<Lexeme> unparsed) {
    /* 
    Assumes:
     * All parenthesis have been stripped and parsed into Expr's.
     * All literals have been parsed into Expr's.

    Parses:
     * Unary operators on expressions.

    Correctness check:
     * Add `prev` to `parsed`, unless it is consumed by the unary parsing logic.
     * Directly lexeme to `parsed` directly iff `prev` will be set to null.
    */
    List<Lexeme> parsed = new ArrayList<>();
    Expr prev = null;

    // Iterate in reverse order.
    for (int i=unparsed.size()-1; i>=0; i--) {
      Lexeme lexeme = unparsed.get(i);
      if (lexeme instanceof Token) {
        Token token = (Token) lexeme;
        if (UNARY_TYPES.contains(token.type) 
            && prev != null 
            && (i==0 || !(unparsed.get(i-1) instanceof Expr))) {
          Unary expr = new Unary(token, prev);
          prev = expr;
          continue;
        }
        parsed.add(prev);
        prev = null;
        parsed.add(token);
      } else if (lexeme instanceof Expr) {
        parsed.add(prev);
        prev = (Expr) lexeme;
      } else {
        throw new ParserException(String.format("Unrecognized lexeme type: %s", lexeme.getClass().getName()));
      }
    }
    parsed.add(prev);

    Collections.reverse(parsed);
    while (parsed.remove(null));
    print(parsed);
    return parsed;
  }
}

class ParserException extends RuntimeException {
  String code;
  int column;

  public ParserException(String message, String code, int column) {
    super(message);
    this.code = code;
    this.column = column;
  }

  public ParserException(String message) {
    this(message, "", -1);
  }
}

/* 


program -> statement* EOF
statement -> { statement* }

statement -> VAR IDENT (= expression)?;
statement -> IF grouping statement
statement -> WHILE grouping statement
statement -> FOR (decl; expr; expr) statement

statement -> expression ;
statement -> PRINT expression ;


expression     → assignment ;
assignment     → IDENT = expression | equality ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | primary ;
primary        → NUMBER | STRING | "true" | "false" | "nil"
               | "(" expression ")" ;
*/

