#!/usr/bin/env python3

def main():
    print("Welcome to the Little Machine Code IDE!")
    while True:
        print("\nMenu:")
        print("1. Edit LMC File")
        print("2. Build & Run LMC Program")
        print("3. Exit")

        choice = input("Enter your choice: ")

        if choice == '1':
            print("Edit LMC File - Not yet implemented.")
        elif choice == '2':
            print("Build & Run LMC Program - Not yet implemented.")
        elif choice == '3':
            print("Exiting IDE. Goodbye!")
            break
        else:
            print("Invalid choice. Please try again.")

if __name__ == "__main__":
    main()
