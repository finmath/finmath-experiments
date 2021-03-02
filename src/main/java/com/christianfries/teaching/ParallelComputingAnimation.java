package com.christianfries.teaching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Java FX Application visualising the different forms of parallel computing
 * (SISD, MIMD, SIMD).
 * 
 * <ul>
 * 	<li>SISD is the classical form of a single sequential processing,</li>
 * 	<li>MIMD is the multi-threadded parallelism known from multi-core processors,</li>
 * 	<li>SIMD is the processing model known from GPGPUs.</li>
 * </ul>
 * 
 * @author Christian Fries
 */
public class ParallelComputingAnimation extends Application { 

	private final int width = 1680;
	private final int height = 1050;
	
	/*
	 * Settings of the current animation (can be changed via RadioButtons)
	 */
	private boolean isSIMD = false;
	private int cycle = 0;
	private int numberOfCores = 3;

	/**
	 * A visualisation of an instruction in a program.
	 * 
	 * An instruction may be a block of instructions, hence it has a variable size.
	 * 
	 * @author fries
	 */
	public interface Instruction {
		/**
		 * Add the instruction to the JavaFX group at a given offset.
		 * 
		 * @param group Group
		 * @param offsetX Offset horizontal
		 * @param offsetY Offset vertical
		 */
		void init(Group group, float offsetX, float offsetY);
		
		/**
		 * Calculate size.
		 */
		float getSize();
		
		/**
		 * Change color of this instruction (highlighting)
		 * @param color The color.
		 */
		void setEnabled(Color color);
		
		/**
		 * Get all lines, including the indicator if the line is active or not.
		 * @return List of lines.
		 */
		List<ProgramLine> getLines();
	}

	/**
	 * An instructions, including an a flag if the line is active.
	 * 
	 * @author Christian Fries
	 */
	public class ProgramLine {
		private final Instruction instruction;
		private final Boolean active;

		public ProgramLine(Instruction instruction, Boolean active) {
			this.instruction = instruction;
			this.active = active;
		}
	}

	public class BlockInstructions implements Instruction {

		private final float indentation = 40.0f;

		private final Boolean active;
		private List<Instruction> instructions = new ArrayList<>();

		public BlockInstructions(List<Instruction> instructions, Boolean active) {
			this.instructions = instructions;
			this.active = active;
		}

		@Override
		public void init(Group group, float offsetX, float offsetY) {
			for(Instruction instruction : instructions) {
				instruction.init(group, offsetX+indentation, offsetY);
				offsetY += instruction.getSize();
			}
		}

		@Override
		public float getSize() {
			float size = 0;
			for(Instruction instruction : instructions) {
				size += instruction.getSize();
			}
			return size;
		}

		@Override
		public void setEnabled(Color color) {
			// TODO Auto-generated method stub

		}

		@Override
		public List<ProgramLine> getLines() {
			List<ProgramLine> lines = new ArrayList<>();
			for(Instruction instruction : instructions) {
				for(ProgramLine sequence : instruction.getLines()) {
					lines.add(new ProgramLine(sequence.instruction, sequence.active & active));
				}
			}
			return lines;
		}
	}

	public class SingleInstruction implements Instruction {

		private final String labelText;
		private final float size;
		private final float width = 200.0f;
		private Rectangle rectangle;

		public SingleInstruction(String labelText, float size) {
			super();
			this.labelText = labelText;
			this.size = size;
		}

		public SingleInstruction() {
			this("", 10.0f);
		}

		@Override
		public void init(Group group, float offsetX, float offsetY) {
			rectangle = new Rectangle(offsetX, offsetY+2, width-offsetX, size-4);
			rectangle.setFill(Color.GRAY);
			Rectangle rectangleInner = new Rectangle(offsetX+10, offsetY+size/2-1, width-offsetX-20, 1);
			rectangleInner.setFill(Color.rgb(255, 255, 255, 0.5f));

			Label label = new Label(labelText);
			label.setAlignment(Pos.BASELINE_CENTER);
			label.setMinWidth(width-offsetX);
			label.setFont(new Font(24));
			label.setTextFill(Color.WHITE);
			label.setTranslateX(offsetX);
			label.setTranslateY(offsetY+2);
			group.getChildren().add(rectangle);
			if(labelText != null && labelText.length() > 1) {
				group.getChildren().add(label);
			}
			else {
//				group.getChildren().add(rectangleInner);
			}
		}

		@Override
		public float getSize() {
			return size;
		}

		@Override
		public void setEnabled(Color color) {
			rectangle.setFill(color);
		}

		@Override
		public List<ProgramLine> getLines() {
			List<ProgramLine> lines = new ArrayList<>();
			lines.add(new ProgramLine(this, true));
			return lines;
		}
	}

	/**
	 * Sample program
	 * 
	 * @author Christian Fries
	 */
	public class Program {

		private final Color color;
		private int line = -1;
		private final BlockInstructions program;

		public Program(Group root, float offsetX, float offsetY, Color color, Boolean condition) {
			this.color = color;

			List<Instruction> instructions1 = new ArrayList<>();
			for(int i=0; i<10; i++) {
				instructions1.add(new SingleInstruction());
			}
			BlockInstructions block1 = new BlockInstructions(instructions1, condition);

			List<Instruction> instructions2 = new ArrayList<>();
			for(int i=0; i<10; i++) {
				instructions2.add(new SingleInstruction());
			}
			BlockInstructions block2 = new BlockInstructions(instructions2, !condition);

			List<Instruction> instructions = new ArrayList<>();
			instructions.add(new SingleInstruction("Input", 40.0f));
			for(int i=0; i<9; i++) {
				instructions.add(new SingleInstruction());
			}
			instructions.add(block1);
			Instruction instructionElse = new SingleInstruction();
			instructions.add(instructionElse);
			instructions.add(block2);
			instructions.add(new SingleInstruction());
			instructions.add(new SingleInstruction());
			instructions.add(new SingleInstruction("Output", 40.0f));

			program = new BlockInstructions(instructions, true);
			program.init(root, offsetX, offsetY);			
		}

		public void setAtRow(int row) {
			for(ProgramLine programLine : program.getLines()) programLine.instruction.setEnabled(Color.GRAY);
			int i = 0;
			for(ProgramLine sequence : program.getLines()) {
				if(sequence.active) {
					if(i++ == row) sequence.instruction.setEnabled(color);
				}
				else {
					if(isSIMD) if(i++ == row) sequence.instruction.setEnabled(Color.LIGHTGRAY);
				}
			}
			line = row;
		}

		public void increment() {
			setAtRow(++line);
		}

		public void decrement() {
			setAtRow(--line);
		}
	}

	/**
	 * The order in which the cores operate on the program. The inner list contains reference to the programs
	 * that are currently active on a core. The size of the inner list cannot be large than the number of cores.
	 */
	List<List<Program>> threadSchedule;

	@Override 
	public void start(Stage stage) throws InterruptedException { 
		//Drawing a Rectangle 

		//Creating a Group object  
		Group root = new Group(); 
		float marginX = (width - (4*100+5*200) ) / 2;

		List<Group> groups = IntStream.range(0, 5).mapToObj(i -> {
			Group group = new Group(); 
			group.setTranslateX(marginX+i*300);
			group.setTranslateY(200.0);
			return group;
		}).collect(Collectors.toList());
		root.getChildren().addAll(groups);

		List<Program> threads = new ArrayList<>();
		threads.add(new Program(groups.get(0), -40, 0, Color.RED, true));
		threads.add(new Program(groups.get(1), -40, 0, Color.GREEN, false));
		threads.add(new Program(groups.get(2), -40, 0, Color.BLUE, true));
		threads.add(new Program(groups.get(3), -40, 0, Color.ORANGE, true));
		threads.add(new Program(groups.get(4), -40, 0, Color.BROWN, false));

		isSIMD = false;
		threadSchedule = getThreadSchedule(threads, 0);

		Label title = new Label("Different Forms of Computing: SISD, MIMD, SIMD");
		title.setTranslateX(width/4);
		title.setTranslateY(30);
		title.setMinWidth(width/2);
		title.setFont(Font.font(32));
		title.setAlignment(Pos.BASELINE_CENTER);
		root.getChildren().add(title);

		Label description = new Label();
		description.setTranslateX(width/4);
		description.setTranslateY(700);
		description.setMinWidth(width/2);
		description.setFont(Font.font(32));
		description.setAlignment(Pos.BASELINE_CENTER);
		root.getChildren().add(description);

		/*
		 * Define UI actions for update and reset
		 */
		Runnable updateUI = () -> {
			description.setText("Clock cycle " + cycle + ".");
		};

		Runnable resetUI = () -> {
			for(Program thread : threads) thread.setAtRow(-1);
			cycle = 0;
			updateUI.run();
		};

		/*
		 * Create UI controls and define actions. 
		 */
		Button buttonForward = new Button("\u25B6");
		buttonForward.setFont(new Font(24));
		buttonForward.setOnAction(event -> {
			if(cycle < threadSchedule.size()) for(Program program : threadSchedule.get(cycle++)) program.increment();
			updateUI.run();
		});

		Button buttonBackward = new Button("\u25C0");
		buttonBackward.setFont(new Font(24));
		buttonBackward.setOnAction(event -> {
			if(cycle > 0) for(Program program : threadSchedule.get(--cycle)) program.decrement();
			updateUI.run();
		});

		AnimationTimer timer = new MyTimer(updateUI);
		ToggleButton buttonOn = new ToggleButton("\u25B6\u25B6");
		buttonOn.setFont(new Font(24));
		buttonOn.setOnAction(event -> {
			if(buttonOn.isSelected()) timer.start();
			else timer.stop();
		});

		RadioButton menuItem1 = new RadioButton("SISD: Single thread single core.");
		menuItem1.setFont(new Font(18));
		menuItem1.setSelected(true);
		RadioButton menuItem2 = new RadioButton("MIMD: Five threads one core.");
		menuItem2.setFont(new Font(18));
		RadioButton menuItem3 = new RadioButton("MIMD: Five threads three cores.");
		menuItem3.setFont(new Font(18));
		RadioButton menuItem4 = new RadioButton("MIMD: Five threads five cores.");
		menuItem4.setFont(new Font(18));
		RadioButton menuItem5 = new RadioButton("SIMD: Five threads five processors.");
		menuItem5.setFont(new Font(18));

		menuItem1.setOnAction(event -> {
			isSIMD = false;
			threadSchedule = getThreadSchedule(threads, 0);
			title.setText("Different Forms of Computing: SISD (1 thread, 1 core)");
			resetUI.run();
		});
		menuItem2.setOnAction(event -> {
			numberOfCores = 1;
			isSIMD = false;
			threadSchedule = getThreadSchedule(threads, 1);
			title.setText("Different Forms of Computing: (Pseudo) MIMD (5 threads, " + numberOfCores + " cores)");
			resetUI.run();
		});
		menuItem3.setOnAction(event -> {
			numberOfCores = 3;
			isSIMD = false;
			threadSchedule = getThreadSchedule(threads, 2);
			title.setText("Different Forms of Computing: MIMD (5 threads, " + numberOfCores + " cores)");
			resetUI.run();
		});
		menuItem4.setOnAction(event -> {
			isSIMD = false;
			numberOfCores = 5;
			threadSchedule = getThreadSchedule(threads, 3);
			title.setText("Different Forms of Computing: MIMD (5 threads, " + numberOfCores + " cores)");
			resetUI.run();
		});
		menuItem5.setOnAction(event -> {
			isSIMD = true;
			threadSchedule = getThreadSchedule(threads, 4);
			title.setText("Different Forms of Computing: SIMD (5 threads, 5 stream processors)");
			resetUI.run();
		});

		ToggleGroup toggleGroup = new ToggleGroup();
		toggleGroup.getToggles().addAll(menuItem1, menuItem2, menuItem3, menuItem4, menuItem5);

		HBox buttonBox = new HBox(buttonBackward, buttonForward, buttonOn);
		buttonBox.setPadding(new Insets(15, 15, 15, 15));
		buttonBox.setSpacing(20);
		buttonBox.setMinWidth(width-2*marginX);
		buttonBox.setAlignment(Pos.BASELINE_CENTER);

		HBox box = new HBox(menuItem1, menuItem2, menuItem3, menuItem4, menuItem5);
		box.setPadding(new Insets(15, 15, 15, 15));
		box.setSpacing(20);
		box.setTranslateX(0);
		box.setMinWidth(width);
		box.setAlignment(Pos.BASELINE_CENTER);

		VBox contorls = new VBox(box, buttonBox);
		contorls.setTranslateX(0);
		contorls.setTranslateY(800);
		contorls.setMinWidth(width);
		contorls.setAlignment(Pos.BASELINE_CENTER);
		root.getChildren().add(contorls);

		Label acknowledgement = new Label("© Copyright 2021 Christan Fries (www.christian-fries.de). CC-SA-BY.");
		acknowledgement.setFont(new Font(12));
		acknowledgement.setMinWidth(1000);
		acknowledgement.setAlignment(Pos.BASELINE_RIGHT);
		acknowledgement.setTranslateX(width-1000-marginX);
		acknowledgement.setTranslateY(650);
		root.getChildren().add(acknowledgement);

		// Creating a scene object 
		Scene scene = new Scene(root, width, height);  

		//Setting title to the Stage 
		stage.setTitle("Parallel Computing Animation"); 

		//Adding scene to the stage 
		stage.setScene(scene); 

		//Displaying the contents of the stage 
		stage.show(); 

		resetUI.run();
	}      

	/**
	 * Create different ways of scheduling the threads.
	 * 
	 * @param threads List of available threads.
	 * @param scheduleID The form of scheduling.
	 * @return
	 */
	private List<List<Program>> getThreadSchedule(List<Program> threads, int scheduleID) {

		List<List<Program>> threadSchedule = new ArrayList<>();

		switch(scheduleID) {
		case 0:
		default:
			IntStream.range(0, 24).forEach(i -> threadSchedule.add(List.of(threads.get(0))));
			IntStream.range(0, 24).forEach(i -> threadSchedule.add(List.of(threads.get(1))));
			IntStream.range(0, 24).forEach(i -> threadSchedule.add(List.of(threads.get(2))));
			IntStream.range(0, 24).forEach(i -> threadSchedule.add(List.of(threads.get(3))));
			IntStream.range(0, 24).forEach(i -> threadSchedule.add(List.of(threads.get(4))));
			break;
		case 1:
		case 2:
		case 3:
			Map<Integer, Integer> instructionsLeft = new HashMap<Integer, Integer>(Map.of(0,24, 1, 24, 2, 24, 3, 24, 4, 24));
			Random random = new Random(3131);
			while(instructionsLeft.values().stream().mapToInt(i -> i).sum() > 0) {
				List<Program> batch = new ArrayList<>();
				List<Integer> threadIDs = IntStream.range(0, threads.size()).boxed().collect(Collectors.toList());
				for(int j=0; j<numberOfCores && threadIDs.size() > 0; j++) {
					int threadIndex = threadIDs.remove(random.nextInt(threadIDs.size()));
					if(instructionsLeft.get(threadIndex) > 0) {
						batch.add(threads.get(threadIndex));
						instructionsLeft.put(threadIndex, instructionsLeft.get(threadIndex)-1);
					}
					else {
						j--;
					}
					if(instructionsLeft.values().stream().mapToInt(i -> i).sum() == 0) break;
				}		
				threadSchedule.add(batch);
			}
			break;
		case 4:
			IntStream.range(0, 34).forEach(i -> threadSchedule.add(List.of(threads.get(0), threads.get(1), threads.get(2), threads.get(3), threads.get(4))));
			break;

		}
		
		return threadSchedule;
	}

	private class MyTimer extends AnimationTimer {

		long last = 0;
		Runnable updateUI;

		public MyTimer(Runnable updateUI) {
			this.updateUI = updateUI;
		}

		@Override
		public void handle(long now) {
			if(now-last > 1000*1000*1000/4) {
				doHandle();
				last = now;
			}
		}

		private void doHandle() {
			if(cycle < threadSchedule.size()) for(Program program : threadSchedule.get(cycle++)) {
				program.increment();
			}
			updateUI.run();
		}
	}

	public static void main(String args[]){ 
		launch(args); 
	} 
}
