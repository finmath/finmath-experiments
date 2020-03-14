finmath experiments
===================

Experiments and demos based on finmath lib.

See also http://finmath.github.io/finmath-experiments/

Projects
--------

**finmath lib**  
    Java library providing implementations of methodologies related to
    mathematical finance, but applicable to other fields (e.g., the
    Monte-Carlo simulation of SDEs and the estimation of conditional
    expectations in Monte-Carlo).
    See http://finmath.net/finmath-lib

**finmath spreadsheets**  
    A collection of spreadsheets building upon *finmath lib* and
    providing end user solutions (e.g, interest rate curve calibration
    or calibration of a forward rate model, aka LIBOR market model).
    See http://finmath.net/spreadsheets/

**finmath experiments**  
    Small experiments, illustrating some aspects of mathematical
    finance. Also illustrates how to use the finmath lib.

**finmath lib plot extensions**
	Convenient abstractions of some plotting libraries and example usages of finmath lib.
	See http://finmath.net/finmath-lib-plot-extensions/
    

Documentation
-------------

-   [finmath lib API documentation][]  
     provides the documentation of the library api.
-   [finmath.net special topics][]  
     cover some selected topics with demo spreadsheets and uml diagrams.
    Some topics come with additional documentations (technical papers).


License
-------

The code of "finmath lib" and "finmath experiments" (packages
`net.finmath.*`) are distributed under the [Apache License version
2.0][], unless otherwise explicitly stated.

  [finmath lib API documentation]: http://www.finmath.net/java/finmath-lib/doc/
  [finmath.net special topics]: http://www.finmath.net/topics
  [Apache License version 2.0]: http://www.apache.org/licenses/LICENSE-2.0.html


Instruction for Contributors for Experiments
-------

Here are a few remarks in case you like to create and contribute a web page with experiments, similar to the ones in the `docs` folder of this repo.

-	Used the same HTML header as in the example on Monte-Carlo simulation, see <a href="montecarlo-blackscholes">montecarlo-blackscholes</a> (adjusting title and description).

-	Use the correct HTML tags for code blocks, i.e. the tag `<div class="codeboxwithheader">` and everything that is inside. Just change the title text and the code inside.

-	Check you page on a mobile device (iPhone, iPad). Long package names or class names can lead to layout issues.

-	If you create plots, the best quality can be achieved by saving the plot as SVG. Use `plot.saveAsSVG(new File(filename), 800, 450))`

