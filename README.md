# Little Machine Compiler Project

This project aims to create a comprehensive development environment for the Little Machine Code language, including a C compiler, a JavaFX-based Integrated Development Environment (IDE), and a VS Code extension for enhanced LMC development.

## Table of Contents
- [Little Machine Compiler Project](#little-machine-compiler-project)
  - [Table of Contents](#table-of-contents)
  - [1. Development Setup](#1-development-setup)
    - [Prerequisites](#prerequisites)
    - [Getting Started](#getting-started)
  - [2. Continuing Development](#2-continuing-development)
    - [Little Machine Compiler (C)](#little-machine-compiler-c)
    - [JavaFX IDE](#javafx-ide)
    - [VS Code Extension](#vs-code-extension)
  - [3. Testing](#3-testing)
    - [Little Machine Compiler (C)](#little-machine-compiler-c-1)
    - [JavaFX IDE](#javafx-ide-1)
    - [VS Code Extension](#vs-code-extension-1)
  - [4. Deployment](#4-deployment)
    - [Little Machine Compiler (C)](#little-machine-compiler-c-2)
    - [JavaFX IDE](#javafx-ide-2)
    - [VS Code Extension](#vs-code-extension-2)

## 1. Development Setup

### Prerequisites
Ensure you have the following installed:
- `gcc` (for the C compiler)
- `make` (for building the C compiler)
- Java Development Kit (JDK) 11 or higher (for JavaFX IDE)
- Apache Maven (for JavaFX IDE)
- Node.js and npm (for VS Code Extension)
- Visual Studio Code (for VS Code Extension development)

### Getting Started
Clone the repository:
```bash
git clone <repository_url>
cd lmcProject
```

## 2. Continuing Development

### Little Machine Compiler (C)
**Location:** `LittleMachineCompiler/`

To build the compiler:
```bash
cd LittleMachineCompiler/
make
```
This will create an executable named `compiler` in the `LittleMachineCompiler/` directory.

To clean built files:
```bash
make clean
```

### JavaFX IDE
**Location:** `ide-javafx/`

To build the JavaFX IDE:
```bash
cd ide-javafx/
mvn clean install
```
This will compile the JavaFX application and package it into a JAR file in the `target/` directory.

To run the JavaFX IDE directly from Maven:
```bash
mvn javafx:run
```

### VS Code Extension
**Location:** `vscode-extension/`

First, install dependencies:
```bash
cd vscode-extension/
npm install
```

To compile the TypeScript source code:
```bash
npm run compile
```
This will output compiled JavaScript files to the `out/` directory.

To watch for changes and recompile automatically during development:
```bash
npm run watch
```

## 3. Testing

### Little Machine Compiler (C)
**Location:** `LittleMachineCompiler/`

The compiler does not have automated tests configured with a test runner. You can manually test it by compiling `.lmc` files and verifying the generated assembly (`.s`) files.

Example:
```bash
cd LittleMachineCompiler/
./compiler test.lmc output.s
```
Then, inspect `output.s` to ensure correctness. You can also try to assemble and run the generated `.s` files using an assembler (e.g., `nasm`) and linker (e.g., `ld`) if you have them set up for your system.

### JavaFX IDE
**Location:** `ide-javafx/`

The JavaFX IDE does not have automated unit or integration tests configured. Manual testing involves running the application (`mvn javafx:run`) and interacting with its features (code editing, syntax checking, running LMC code, debugging).

### VS Code Extension
**Location:** `vscode-extension/`

To run the extension tests:
```bash
cd vscode-extension/
npm test
```
This will launch a special VS Code instance to run the tests defined in your `src/test/` directory (if any).

To debug the extension:
1. Open the `vscode-extension/` folder in VS Code.
2. Press `F5` to launch a new VS Code window with your extension loaded.
3. In the new window, open an `.lmc` file and test your extension's features. You can set breakpoints in your extension's TypeScript code in the original VS Code window.

## 4. Deployment

### Little Machine Compiler (C)
**Location:** `LittleMachineCompiler/`

The compiler is a single executable. To "deploy" it, you would typically copy the compiled `compiler` executable to a desired location in your system's PATH or distribute it as part of a larger package.

Example (copy to a bin directory):
```bash
cp LittleMachineCompiler/compiler /usr/local/bin/
```
(Note: This requires appropriate permissions.)

### JavaFX IDE
**Location:** `ide-javafx/`

To create a runnable JAR file for distribution:
```bash
cd ide-javafx/
mvn clean package
```
This will generate `lmc-ide-1.0-SNAPSHOT.jar` (or similar) in the `target/` directory. This JAR can be run on any system with a compatible Java Runtime Environment (JRE) installed.

Example:
```bash
java -jar target/lmc-ide-1.0-SNAPSHOT.jar
```

For a more complete native application package (e.g., `.exe`, `.dmg`, `.deb`), you would typically use the `jlink` or `jpackage` tools from the JDK, or a more advanced Maven plugin for native packaging. This project's `pom.xml` does not currently include such configurations.

### VS Code Extension
**Location:** `vscode-extension/`

To package the extension into a `.vsix` file for distribution:
1. Ensure you have the VS Code Extension `vsce` (Visual Studio Code Extension Manager) installed globally:
   ```bash
   npm install -g vsce
   ```
2. Navigate to the extension directory and package:
   ```bash
   cd vscode-extension/
   vsce package
   ```
   This will create a `.vsix` file (e.g., `lmc-ide-extension-0.0.1.vsix`).

To install the `.vsix` file in VS Code:
1. Open VS Code.
2. Go to the Extensions view (`Ctrl+Shift+X` or `Cmd+Shift+X`).
3. Click on the `...` (More Actions) menu at the top of the Extensions sidebar.
4. Select "Install from VSIX...".
5. Choose your generated `.vsix` file.

To publish the extension to the Visual Studio Code Marketplace, you would use `vsce publish` after setting up a publisher account. Refer to the official VS Code documentation for detailed steps on publishing.
