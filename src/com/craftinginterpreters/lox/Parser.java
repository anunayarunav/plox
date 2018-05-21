package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import static com.craftinginterpreters.lox.TokenType.*;

/**
  * Grammar so far

  * Program -> Declaration* EOF;
  * Declaration -> VarDecl | Statement
  * VarDecl -> VAR IDENTIFIER ( "=" Expression) ? ";"
  * Statement -> ExpressionStatement | PrintStatement
  * ExpressionStatement -> Expression ";"
  * PrintStatement -> "print" Expression ";"
  * Expression -> Comma expression
  * Comma Expression -> Ternary Expression (, Ternary Expression) *
  * Ternary Expression -> Equality ( "?" Equality ":" Equality) *, right associative
  * Equality -> Comparison ( "==" | "!=" Comparison ) *, left associative
  * Comparison -> Addition ( "!=" | ">" | ">=" | "<=" Addition) *, left associative
  * Addition -> Multiplication ( "+" | "-" Multiplication) *, left associative
  * Multiplication -> Unary ( "*" | "/" Unary) *, left associative
  * Unary -> ("!" | "-") Unary | Primary
  * Primary -> NUMBER | STRING | "TRUE" | "FALSE" | "NIL" | "(" Expression ")" | IDENTIFIER
**/
class Parser {

  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {

    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Stmt declaration() {
    try {
      if(match(VAR)) return varDeclaration();

      return statement();
    }
    catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expected variable name.");

    Expr initializer = null;
    if(match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after value.");

    return new Stmt.Var(name, initializer);
  }

  private Stmt statement() {
    if(match(PRINT)) {
      return printStatement();
    }

    return expressionStatement();
  }

  private Stmt printStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(expr);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Expr expression() {
    return comma();
  }

  //comma support added by @Anunay
  private Expr comma() {
    Expr expr = ternary();

    while(match(COMMA)) {
      Token operator = previous();
      Expr right = ternary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  //ternary support added by @Anunay
  private Expr ternary() {

    LinkedList<Expr> stack = new LinkedList<Expr>();

    Expr e = equality();
    stack.add(e);

    Token loperator = null, roperator = null;

    while(match(QUESTION)) {
      loperator = previous();
      Expr middle = equality();
      consume(COLON, "Expect ':' after expression.");
      roperator = previous();
      Expr right = equality();

      stack.add(middle);
      stack.add(right);
    }

    Expr expr = stack.removeLast();

    while(!stack.isEmpty()) {
      Expr middle = stack.removeLast();
      Expr left = stack.removeLast();

      expr = new Expr.Ternary(left, loperator, middle, roperator, expr);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = addition();
    while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = addition();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr addition() {
    Expr expr = multiplication();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = multiplication();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr multiplication() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if(match(MINUS, BANG)) {
      Token operator = previous();
      Expr right = unary();
      Expr expr = new Expr.Unary(operator, right);

      return expr;
    }

    return primary();
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if(match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    //report binary operand starting at the beginning
    if(match(BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL,
            LESS, LESS_EQUAL, MINUS, PLUS, SLASH, STAR)) {

      Token token = previous();
      Lox.error(token.line, "Error at '" + token.lexeme + "', Binary Operator not expected at the beginning of the expression");
      return expression();
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private boolean check(TokenType tokenType) {
    if (isAtEnd()) return false;
    return peek().type == tokenType;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}
