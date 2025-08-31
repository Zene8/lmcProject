# Development Plan for LMC IDE

## Current Status
The project is currently in a state where the core JavaFX IDE structure is in place, but there are persistent compilation errors preventing a successful build. The primary focus is to resolve these compilation issues across all modules, particularly in `App.java`, `UIController.java`, `LMCIDEFeatures.java`, `FileManager.java`, `LMCExecutor.java`, and `LMCInterpreter.java`.

## Immediate Next Steps (Phase 1: Compilation & Basic Functionality)

1.  **Resolve All Compilation Errors:**
    *   Systematically go through each reported error from Maven (`mvn clean install`).
    *   Address missing imports (e.g., `Preferences`, `URL`, `CompletableFuture`, `Collectors`, JavaFX controls like `SplitPane`, `TabPane`, `TreeView`, `Node`, `Label`, `TextField`, `Button`, `Image`, `ImageView`, `SeparatorMenuItem`, `Color`, `Circle`, `Tooltip`, `LineNumberFactory`).
    *   Correct method signature mismatches (e.g., `setControls` in `LMCExecutor`).
    *   Ensure all classes and methods are properly closed with `}`.
    *   Verify correct usage of `Map` and `HashMap` imports.
    *   Ensure `interpreter.reset()` calls are correctly mapped to `interpreter.resetProgram()`.
    *   Fix any unclosed string literals.

2.  **Verify Basic UI Initialization:**
    *   Once compiled, ensure the application launches without runtime errors.
    *   Verify that the main window, menu bar, control panel, editor tab pane, tools sidebar, and memory view are visible and correctly laid out.

3.  **Test Core File Operations:**
    *   Confirm "New File", "Open File", "Save File", and "Close Tab" functionalities work as expected.
    *   Verify that new tabs are created and files can be loaded and saved.

4.  **Test Basic LMC Execution Flow:**
    *   Load a simple LMC program.
    *   Verify "Start", "Stop", "Step", and "Reset" buttons interact correctly with the interpreter.
    *   Check console output and memory visualizer updates.

## High-Level Roadmap (Future Phases)

### Phase 2: Feature Refinement & Stability
*   **Enhanced Syntax Highlighting:** Improve accuracy and add more LMC-specific highlighting rules.
*   **Improved Error Reporting:** Provide more user-friendly and precise error messages from the parser and interpreter.
*   **Debugger Functionality:**
    *   Implement breakpoint toggling directly in the editor.
    *   Step-through execution with visual indication of current instruction.
    *   Variable inspection (if LMC supports named variables beyond labels).
*   **Input/Output Handling:** More robust input dialogs and clearer output display.
*   **File Explorer Enhancements:** Allow creating new files/folders directly from the IDE.

### Phase 3: Advanced IDE Features
*   **Code Completion/Suggestions:** Expand the current autocomplete to be more context-aware.
*   **Refactoring Tools:** Simple rename functionality for labels.
*   **Project Management:** Better handling of multi-file LMC projects.
*   **Integrated Help/Documentation:** Quick access to LMC instruction set and IDE features.
*   **Theming Options:** More comprehensive theme customization.

### Phase 4: Performance & Optimization
*   **Large File Handling:** Optimize performance for very large LMC assembly files.
*   **Interpreter Performance:** Look for bottlenecks in the LMC interpreter for faster execution.

## Technologies Used
*   **Frontend:** JavaFX
*   **Build Tool:** Apache Maven
*   **Language:** Java 17+
*   **Code Editor Component:** RichTextFX

This plan will guide the development process, ensuring a stable and feature-rich LMC IDE.