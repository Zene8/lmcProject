# LMC IDE

## Little Man Computer Integrated Development Environment

This project is a desktop-based Integrated Development Environment (IDE) for the Little Man Computer (LMC). The LMC is an instructional model of a computer, used to teach students the basic concepts of computer architecture and assembly language programming. This IDE aims to provide a user-friendly environment for writing, assembling, executing, and debugging LMC programs.

## Features

*   **Code Editor:** A dedicated editor for writing LMC assembly code with syntax highlighting.
*   **Assembler:** Converts LMC assembly code into machine code that the LMC interpreter can understand.
*   **Interpreter/Simulator:** Executes LMC machine code, simulating the behavior of the Little Man Computer.
*   **Memory Visualizer:** A visual representation of the LMC's memory, showing the contents of each memory address during program execution.
*   **Input/Output Console:** A console for providing input to LMC programs and displaying their output.
*   **Basic Debugging:** Step-by-step execution and error highlighting to assist in debugging LMC programs.
*   **File Management:** Basic functionalities to create, open, save, and manage LMC assembly files.
*   **Theming:** Support for different UI themes (e.g., light and dark mode).
*   **Code Snippets:** Pre-defined code snippets for common LMC programming tasks.

## Getting Started

### Prerequisites

*   Java Development Kit (JDK) 17 or higher
*   Apache Maven (for building the project)

### Building the Project

1.  **Clone the repository:**
    ```bash
    git clone <repository_url>
    cd lmcProject/ide-javafx
    ```

2.  **Build with Maven:**
    ```bash
    mvn clean install
    ```
    This command will compile the project, run tests, and package it into a JAR file.

### Running the IDE

After a successful build, you can run the IDE using the Maven Exec Plugin:

```bash
cd ide-javafx
mvn javafx:run
```

Alternatively, you can run the compiled JAR file directly (path may vary based on your Maven configuration):

```bash
java -jar target/lmc-ide-1.0-SNAPSHOT.jar
```

## Project Structure

*   `ide-javafx/`: Contains the source code for the JavaFX desktop application.
    *   `pom.xml`: Maven project configuration file.
    *   `src/main/java/com/lmc/ide/`: Main Java source files for the IDE.
    *   `src/main/resources/`: UI and other resources (CSS, icons).
*   `LittleMachineCompiler/`: (Potentially) Contains a separate command-line compiler or related tools.
*   `vscode-extension/`: (Potentially) Contains source code for a VS Code extension for LMC.

## Contributing

Contributions are welcome! Please refer to `DEVELOPMENT_PLAN.md` and `FUTURE_FEATURES.md` for more details on the project's direction and potential areas for contribution.

## License

[Specify your project's license here, e.g., MIT, Apache 2.0, etc.]
