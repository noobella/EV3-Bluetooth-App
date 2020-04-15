# EV3 Bluetooth App
***

Go to directory app/src/main/java/uk/ac/rhul/cyclingprofessor/ev3sensors/ for added code

1. Added in Solver.java and Sudoku.java (credit to https://github.com/vincentg). Solver.java contains the code to solve the sudoku while Sudoku.java is the class that contains the method you’d call in your code which makes use of the Solver.java class. 

2. Changes made to MainActivity.java between lines 103 and 182. Removed the camera and added in a sudoku grid instead. Pressing the button ‘send’ on that activity will trigger the app to call the method from Sudoku.java to solve the sudoku. The returned result is stored in a string and sent over Bluetooth to the robot.

