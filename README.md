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
- Java Development Kit (JDK) 17 or higher (for JavaFX IDE)
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
Note: Running the JavaFX IDE requires a graphical display environment. If you encounter an "Unable to open DISPLAY" error, you are likely in a headless environment. The application cannot be run directly in such environments.

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

This section outlines how to verify the functionality of each component of the Little Machine Compiler project.

### Little Machine Compiler (C)
**Location:** `LittleMachineCompiler/`

The C compiler currently relies on manual verification due to the absence of an automated test runner. You can test its functionality by compiling `.lmc` source files and then examining the generated `.s` assembly files.

**Steps to Test:**

1.  **Navigate to the compiler directory:**
    ```bash
    cd LittleMachineCompiler/
    ```
2.  **Build the compiler executable:**
    If you haven't already, compile the source code using `make`:
    ```bash
    make
    ```
    This command will create an executable named `compiler` in the current directory.
3.  **Compile an LMC source file:**
    Use the compiled `compiler` executable to translate an `.lmc` file into an `.s` assembly file. For example, to compile `test_all_instructions.lmc` (a comprehensive test file included in the project):
    ```bash
    ./compiler test_all_instructions.lmc test_all_instructions.s
    ```
    You can replace `test_all_instructions.lmc` with any other `.lmc` file you wish to test, and `test_all_instructions.s` with your desired output filename.
4.  **Inspect the generated assembly:**
    Open the generated `.s` file (e.g., `test_all_instructions.s`) in a text editor. Carefully review its content to ensure that the assembly instructions correctly correspond to the original LMC code. This manual inspection is crucial for verifying the compiler's translation accuracy.
    
    *Optional:* For more advanced verification, if you have an assembler (e.g., `nasm`) and linker (e.g., `ld`) set up for your system, you can attempt to assemble and run the generated `.s` files to confirm their execution behavior.

### JavaFX IDE
**Location:** `ide-javafx/`

The JavaFX IDE currently does not have automated unit or integration tests. Verification involves running the application and manually interacting with its various features to ensure they function as expected.

**Steps to Test:**

1.  **Navigate to the IDE directory:**
    ```bash
    cd ide-javafx/
    ```
2.  **Build the JavaFX application:**
    Use Maven to clean any previous builds and install the application dependencies:
    ```bash
    mvn clean install
    ```
    This command will compile the JavaFX application and package it, typically into a JAR file within the `target/` directory.
3.  **Run the JavaFX IDE:**
    Launch the IDE directly using Maven:
    ```bash
    mvn javafx:run
    ```
    Note: Running the JavaFX IDE requires a graphical display environment. If you encounter an "Unable to open DISPLAY" error, you are likely in a headless environment. The application cannot be run directly in such environments.
    This will start the JavaFX application.
4.  **Perform manual testing:**
    Once the IDE window appears, thoroughly interact with all its functionalities. This includes:
    *   **Code Editing:** Type and edit LMC code in the editor. Verify syntax highlighting, basic text manipulation, and responsiveness.
    *   **Syntax Checking:** Observe if the IDE provides real-time feedback on syntax errors or warnings as you type.
    *   **Running LMC Code:** If the IDE has a feature to execute LMC code, run some sample programs and verify their output.
    *   **Debugging Features:** If debugging capabilities are implemented, test setting breakpoints, stepping through code, and inspecting variable values.
    *   **File Operations:** Test opening, saving, and creating new LMC files.

### VS Code Extension
**Location:** `vscode-extension/`

The VS Code extension can be verified through both automated tests (if implemented) and manual debugging within a special VS Code instance.

**Steps to Test:**

1.  **Navigate to the extension directory:**
    ```bash
    cd vscode-extension/
    ```
2.  **Install Node.js dependencies:**
    Ensure all required packages are installed:
    ```bash
    npm install
    ```
3.  **Run automated tests (if available):**
    If the extension includes automated tests (typically located in `src/test/`), you can run them using:
    ```bash
    npm test
    ```
    This command will launch a dedicated VS Code instance to execute the defined tests and report their results.
4.  **Manually test and debug the extension:**
    This is the primary method for interactive testing and development:
    *   **Open the extension project in VS Code:** In your main VS Code window, open the `/home/astra/projects/lmcProject/vscode-extension/` folder.
    *   **Launch a debug session:** Press `F5`. This action will compile the extension (if necessary) and open a new, separate VS Code window (often called the "Extension Development Host"). This new window has your extension loaded and active.
    *   **Test extension features:** In the "Extension Development Host" window, open an `.lmc` file (you can create a new one or open an existing one from the `LittleMachineCompiler/` directory). Interact with your extension's features, such as:
        *   **Syntax Highlighting:** Verify that LMC keywords, instructions, and data are correctly colored.
        *   **Autocompletion/IntelliSense:** Check if the extension provides relevant suggestions as you type.
        *   **Diagnostics/Linting:** Confirm that syntax errors or semantic issues in your LMC code are highlighted.
        *   **Hover Information:** If implemented, check if hovering over LMC elements provides useful information.
        *   **Commands:** Test any custom commands the extension registers (e.g., via the Command Palette `Ctrl+Shift+P`).
    *   **Set breakpoints (for debugging):** In your *original* VS Code window (where you opened the `vscode-extension/` project), you can set breakpoints in your TypeScript source files (`.ts` files in the `src/` directory). When you perform an action in the "Extension Development Host" window that triggers the code at a breakpoint, execution will pause in your original window, allowing you to inspect variables and step through your code.

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
