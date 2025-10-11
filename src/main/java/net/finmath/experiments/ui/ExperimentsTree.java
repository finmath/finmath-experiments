package net.finmath.experiments.ui;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * UI Menu of different UI Experiments
 * 
 * @author Christian Fries
 */
public class ExperimentsTree extends Application {

	/**
	 * Encapsulates construction of UI Experiments and specifies if
	 * the experiment is lightwight enough (memory wise) to be cashed.
	 */
	class ExperimentApplication {

		private static final int maxCacheDepth = 20;

		/** LRU-Cache (accessOrder=true). */
		private static final LinkedHashMap<String, Parent> cache = new LinkedHashMap<>(16, 0.75f, true) {
			private static final long serialVersionUID = -3650668979377727071L;

			@Override protected boolean removeEldestEntry(Map.Entry<String, Parent> eldest) {
				// LRU cache eviction
				return size() > maxCacheDepth;
			}
		};

		/**
		 * Factory generating a UI component.
		 */
		private final Supplier<Parent> factory;

		/**
		 * Cache weight - currently only 0 = no caching and > 0 caching
		 */
		private final int cacheDepth;

		/**
		 * Create an experiment.
		 */
		public ExperimentApplication(Supplier<Parent> factory, int cacheDepth) {
			this.factory = Objects.requireNonNull(factory, "factory");
			this.cacheDepth = Math.max(0, cacheDepth);
		}

		/** 
		 * Returns an instance from the cache for a given name, otherwise creates it with the factory.
		 */
		public synchronized Parent getView(String name) {
			Parent p = cache.get(name);
			if (p != null) return p;
			p = factory.get();
			if(cacheDepth > 0) cache.put(name, p); // may trigger cache evict
			return p;
		}

		/* 
		 * Cache utilities
		 */

		public synchronized void invalidate(String name) { cache.remove(name); }
		public synchronized void clear() { cache.clear(); }
		public synchronized int cachedCount() { return cache.size(); }
		public int getCacheDepth() { return cacheDepth; }
	}

	/*
	 * MODEL of experiments
	 */
	private final Map<String, Object> model = mapOf(
			"DICE Model (Climate School)", mapOf(
					"Info", (Supplier<Parent>) () -> getInfo()
					//,
					//					"One Parametric Abatement Model (new Window)", DICEAbatementTimeExperimentUI.class
					),
			"Interest Rates", mapOf(
					"Simulation of Hull White Paths", new ExperimentApplication(() -> new InterestRatesHullWhiteSimulationPathOfShortRate().createContent(), 1),
					"(tba)", (Runnable) () -> System.out.println("Will be added soon.")
					)
			);

	private Parent getInfo() {
		VBox box = new VBox(
				new Label("Collection of Parameter Experiments based on Models from finmath lib"),
				new Label("Version 2025-10-09"),
				new Label("Select a topic on the left; set the parameters or select calculate.")
				);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(12));
		return box;
	}

	// Helper to iteratively generate LinkedHashMap
	private static LinkedHashMap<String, Object> mapOf(Object... kv) {
		var m = new LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	private final BorderPane root = new BorderPane();
	private final StackPane contentPane = new StackPane(new Label("Main Content"));

	// Record with a label and arbitrary payload
	private record Entry(String label, Object payload) {}

	@Override
	public void start(Stage stage) {
		// Tree links
		TreeItem<Entry> rootItem = toTree("ROOT", model);
		TreeView<Entry> tree = new TreeView<>(rootItem);
		tree.setShowRoot(false);
		tree.setCellFactory(tv -> new TreeCell<>() {
			@Override protected void updateItem(Entry e, boolean empty) {
				super.updateItem(e, empty);
				setText(empty || e == null ? null : e.label());
			}
		});
		rootItem.getChildren().forEach(it -> it.setExpanded(true));

		tree.setOnMouseClicked(e -> runIfLeaf(tree.getSelectionModel().getSelectedItem(), stage));

		// --- NEU: SplitPane für frei einstellbare Sidebar-Breite
		Preferences prefs = Preferences.userNodeForPackage(ExperimentsTree.class);
		double divider = prefs.getDouble("sidebar.divider", 0.25); // 25% default

		// Set min and max width of left menu
		tree.setMinWidth(300);
		tree.setMaxWidth(600);

		// Select default (first child)
		javafx.application.Platform.runLater(() -> {
			TreeItem<Entry> defaultItem = rootItem.getChildren().isEmpty() ? null : rootItem.getChildren().get(0);
			while(!defaultItem.getChildren().isEmpty()) defaultItem = defaultItem.getChildren().get(0);
			if (defaultItem != null) {
				tree.getSelectionModel().select(defaultItem);
				tree.getFocusModel().focus(tree.getRow(defaultItem));
				tree.scrollTo(tree.getRow(defaultItem));
			}
		});		

		SplitPane splitPane = new SplitPane(tree, contentPane);
		splitPane.setDividerPositions(divider);

		// Divider-Position persistieren
		ChangeListener<Number> persist = (obs, o, n) -> prefs.putDouble("sidebar.divider", n.doubleValue());
		splitPane.getDividers().get(0).positionProperty().addListener(persist);

		// Context menu
		MenuItem resetWidth = new MenuItem("Reset sidebar width");
		resetWidth.setOnAction(e -> splitPane.setDividerPositions(0.25));
		tree.setContextMenu(new ContextMenu(resetWidth));

		root.setCenter(splitPane);

		Scene scene = new Scene(root, 1024, 520);
		stage.setScene(scene);
		stage.setTitle("finmath Numerical Experiments");
		stage.show();
	}

	/**
	 * Action for a leaf node.
	 * 
	 * @param item The selected item (item.getValue() is the payload (Runnable or ExperimentApplication).
	 * @param owner The owning stage.
	 */
	private void runIfLeaf(TreeItem<Entry> item, Stage owner) {
		if (item == null) return;

		boolean isLeaf = item.getChildren().isEmpty();
		if (!isLeaf) { item.setExpanded(!item.isExpanded()); return; }

		Object payload = item.getValue().payload();
		try {
			if (payload instanceof Runnable r) {
				r.run();
			}
			else if (payload instanceof ExperimentApplication exp) {
				String key = pathFor(item);				// unique name of the experiment
				Parent content = exp.getView(key);		// take content from cache or build it
				showInCenter(content);
			}
			else if (payload instanceof Supplier<?> s) {
				Object obj = s.get();
				if (obj instanceof Parent p) showInCenter(p);
				else throw new IllegalArgumentException("Supplier must return Parent: " + obj);
			}
			else if (payload instanceof Class<?> c && Application.class.isAssignableFrom(c)) {
				Parent embedded = tryStaticContent((Class<?>) c);
				if (embedded != null) {
					showInCenter(embedded);
				} else {
					@SuppressWarnings("unchecked")
					Class<? extends Application> appClass = (Class<? extends Application>) c;
					Application app = appClass.getDeclaredConstructor().newInstance();
					Stage s = new Stage();
					s.initOwner(owner);
					app.start(s);
					s.show();
				}

			} else {
				throw new IllegalArgumentException("Unsupported payload: " + payload);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			new Alert(Alert.AlertType.ERROR, "Error loading: " + ex.getMessage()).showAndWait();
		}
	}

	private String pathFor(TreeItem<Entry> item) {
		java.util.Deque<String> parts = new java.util.ArrayDeque<>();
		for (TreeItem<Entry> it = item; it != null; it = it.getParent()) {
			Entry e = it.getValue();
			if (e != null && e.label() != null && !"ROOT".equals(e.label())) {
				parts.addFirst(e.label());
			}
		}
		return String.join("/", parts);
	}

	private void showInCenter(Parent content) {
		contentPane.getChildren().setAll(content);
		StackPane.setMargin(content, new javafx.geometry.Insets(8));
	}

	// Searches zero-arg static method that creates returns a Parent (z.B. createContent())
	private Parent tryStaticContent(Class<?> cls) {
		for (String name : new String[]{"createContent", "content", "buildUI"}) {
			try {
				Method m = cls.getDeclaredMethod(name);
				if (Parent.class.isAssignableFrom(m.getReturnType())) {
					Object res = m.invoke(null);
					return (Parent) res;
				}
			} catch (NoSuchMethodException ignored) {
			} catch (Exception e) {
				throw new RuntimeException("Error calling " + name + "()", e);
			}
		}
		return null;
	}

	// --- Map -> Tree ---------------------------------------------------------
	private TreeItem<Entry> toTree(String label, Object node) {
		if (node instanceof Map<?,?> m) {
			TreeItem<Entry> parent = new TreeItem<>(new Entry(label, null));
			m.forEach((k, v) -> parent.getChildren().add(toTree(String.valueOf(k), v)));
			return parent;
		}
		// Blatt: alles andere
		return new TreeItem<>(new Entry(label, node));
	}

	public static void main(String[] args) { launch(args); }
}
