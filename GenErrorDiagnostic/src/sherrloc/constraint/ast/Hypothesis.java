package sherrloc.constraint.ast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sherrloc.constraint.analysis.PathFinder;
import sherrloc.constraint.analysis.ShortestPathFinder;
import sherrloc.graph.ConstraintGraph;
import sherrloc.graph.Node;
import sherrloc.graph.Variance;

/**
 * Hypothesis consists a conjunction of inequalities
 */
public class Hypothesis {
	private Set<Inequality> assertions;
	private List<Axiom> axioms;
	private Set<Element> elmts;			// elements whose relation is of interest
	private ConstraintGraph graph = null;
	private PathFinder finder = null;
	private Hypothesis parent = null; 	// used to reduce shared environments
										// (e.g., to store global assumptions)
	private boolean USE_GRAPH = true;	// set true to use hypothesis graph to infer provable relations
	private Set<Function> functions;

	/**
	 * Construct an empty hypothesis
	 */
	public Hypothesis() {
		this(new ArrayList<Axiom>());
	}
	
	/**
	 * Construct an empty hypothesis
	 */
	public Hypothesis(List<Axiom> axioms) {
		assertions = new HashSet<Inequality>();
		this.axioms = axioms;
		elmts = new HashSet<Element>();
		functions = new HashSet<Function>();
	}

	/**
	 * Add an inequality to the hypothesis
	 * 
	 * @param ieq
	 *            An inequality to be added
	 */
	public void addInequality(Inequality ieq) {
		Inequality toadd = ieq.baseInequality();
		assertions.add(toadd);
		elmts.add(toadd.e1);
		elmts.add(toadd.e2);
	}
	
	/**
	 * Add axioms to the hypothesis
	 * 
	 * @param axiom
	 *            Axioms to be added
	 */
	public void addAxioms(List<Axiom> axioms) {
		this.axioms.addAll(axioms);
	}
	
	/**
	 * @param s
	 *            A set of elements
	 */
	public void addElements (Set<Element> elements) {
		for (Element ele : elements) {
			addElement(ele);
		}
	}
	
	/**
	 * @param ele
	 *            One element
	 */
	public void addElement (Element ele) {
		if (parent == null)
			elmts.add(ele.getBaseElement());
		else
			parent.addElement(ele.getBaseElement());
	}

	/**
	 * Add all inequalities in <code>e</code> to the hypothesis
	 * 
	 * @param e
	 *            An hypothesis to be added
	 */
	public void addEnv(Hypothesis e) {
		if (parent == null) {
			parent = e;
		} else {
			assertions.addAll(e.getInequalities());
			elmts.addAll(e.elmts);
			axioms.addAll(e.axioms);
		}
	}
	
	/**
	 * Add all functions to the environment
	 * 
	 * @param functions
	 *            A set of functions
	 */
	public void addFunctions (Collection<Function> functions) {
		this.functions.addAll(functions);
	}

	/**
	 * Return a fresh hypothesis where an inequality <code>e1 <= e2</code> is
	 * added to the current hypothesis
	 * 
	 * @param e1
	 *            Element on LHS
	 * @param e2
	 *            Element on RHS
	 * @return A fresh hypothesis where an inequality <code>e1</code> <=
	 *         <code>e2</code> is added to the current hypothesis
	 */
	public Hypothesis addLeq(Element e1, Element e2) {
		Hypothesis h = new Hypothesis();
		h.addEnv(this);
		h.addInequality(new Inequality(e1.getBaseElement(),
				e2.getBaseElement(), Relation.LEQ));
		return h;
	}
	
	/**
	 * @return A string of all inequalities in hypothesis
	 */
	public String inequalityString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Assertions:\n");
		for (Inequality e : assertions) {
			sb.append(e + "\n");
		}
		return sb.toString();
	}

	/**
	 * Test if the relation <code>e1</code> <= <code>e2</code> can be derived
	 * from the hypothesis
	 * 
	 * @param p1
	 *            Element on LHS
	 * @param p2
	 *            Element on RHS
	 * @return True if <code>p1 <= p2</code>
	 */
	public boolean leq(Element p1, Element p2) {
		return leq(p1, p2, true);		
	}
	
	
	private boolean leq(Element p1, Element p2, boolean rec) {
		Element e1 = p1.getBaseElement();
		Element e2 = p2.getBaseElement();
		
		if (USE_GRAPH) {
			if (e1.equals(e2))
				return true;

			if (e1.isBottom() || e2.isTop())
				return true;

			if (e1 instanceof Variable || e2 instanceof Variable) {
				return true;
			}

			if (e1 instanceof ConstructorApplication
					&& e2 instanceof ConstructorApplication) {
				if (((ConstructorApplication) e1).getCons().equals(
						((ConstructorApplication) e2).getCons()) && (e1.hasVars() || e2.hasVars()))
					return true;
			}

			if (rec && leqApplyAssertions(e1, e2))
				return true;
			return false;
		}
		else {
		// simple cases
		if (e1.equals(e2))
			return true;

		if (e1.isBottom() || e2.isTop())
			return true;

		if (e1 instanceof Variable || e2 instanceof Variable) {
			return true;
		}

		// an assumption can be made on the join/meet/constructors, so apply
		// the assumptions first
		if (rec && leqApplyAssertions(e1, e2))
			return true;

		// In principle, all partial orderings can be inferred on the hypothesis
		// graph. But since the graph is constructed from constraints, a
		// relation can be missing if the element to be tested on does not
		// present in the graph (e.g., A <= A join B). One way is to
		// incrementally add the extra nodes to a saturated constraint graph.
		// Here, we apply the rules for constructors, joins and meets directly
		// for simplicity.

		// constructor mismatch
		if (e1 instanceof ConstructorApplication
				&& e2 instanceof ConstructorApplication) {
			if (!((ConstructorApplication) e1).getCons().equals(
					((ConstructorApplication) e2).getCons()))
				return false;
		}

		// break constructor application into the comparison on components
		if (e1 instanceof ConstructorApplication
				&& e2 instanceof ConstructorApplication) {
			List<Element> l1 = ((ConstructorApplication) e1).elements;
			List<Element> l2 = ((ConstructorApplication) e2).elements;
			Variance variance = ((ConstructorApplication) e1).getCons().getVariance();
			for (int i = 0; i < l1.size(); i++) {
				if (variance.equals(Variance.POS) && !leq(l1.get(i), l2.get(i)))
					return false;
				else if (variance.equals(Variance.NEG) && !leq(l2.get(i), l1.get(i)))
					return false;
				else if (variance.equals(Variance.NONE) && (!leq(l2.get(i), l1.get(i)) || !leq(l1.get(i), l2.get(i))))
					return false;
			}
			return true;
		}

		// apply the inference rules for joins and meets
		if (e1 instanceof JoinElement) {
			boolean succ = true;
			for (Element e : ((JoinElement) e1).getElements())
				if (!leq(e, e2, rec)) {
					succ = false;
					break;
				}
			if (succ)
				return true;
		} else if (e1 instanceof MeetElement) {
			for (Element e : ((MeetElement) e1).getElements())
				if (leq(e, e2, rec))
					return true;
		}

		if (e2 instanceof JoinElement) {
			for (Element e : ((JoinElement) e2).getElements())
				if (leq(e1, e, rec))
					return true;
		} else if (e2 instanceof MeetElement) {
			boolean succ = true;
			for (Element e : ((MeetElement) e2).getElements())
				if (!leq(e1, e, rec)) {
					succ = false;
					break;
				}
			if (succ)
				return true;
		}

		return false;
		}
	}

	/**
	 * @return All inequalities made in the hypothesis
	 */
	public Set<Inequality> getInequalities() {
		if (parent == null)
			return assertions;
		else {
			Set<Inequality> ret = new HashSet<Inequality>();
			ret.addAll(assertions);
			ret.addAll(parent.getInequalities());
			return ret;
		}
	}
	
	/**
	 * @return All elements in the hypothesis
	 */
	public Set<Element> getElements() {
		if (parent == null)
			return elmts;
		else {
			Set<Element> ret = new HashSet<Element>();
			ret.addAll(elmts);
			ret.addAll(parent.getElements());
			return ret;
		}
	}
	
	/**
	 * @return All axioms in the hypothesis
	 */
	public List<Axiom> getAxioms() {
		if (parent == null)
			return axioms;
		else {
			List<Axiom> ret = new ArrayList<Axiom>();
			ret.addAll(axioms);
			ret.addAll(parent.getAxioms());
			return ret;
		}
	}
	
	/**
	 * @return All functions in the hypothesis
	 */
	public Set<Function> getFunctions() {
		if (parent == null)
			return functions;
		else {
			Set<Function> ret = new HashSet<Function>();
			ret.addAll(functions);
			ret.addAll(parent.getFunctions());
			return ret;
		}
	}

	/**
	 * Saturate a hypothesis graph to test if <code>e1<=e2</code> can be
	 * inferred
	 * 
	 * @param e1
	 *            Element on LHS
	 * @param e2
	 *            Element on RHS
	 * @return True if <code>e1 <= e2</code> can be inferred from the hypothesis
	 */
	private boolean leqApplyAssertions(Element e1, Element e2) {

		Set<Function> funcs = getFunctions();
		if (finder == null) {
			graph = new ConstraintGraph (null, getAxioms());
			for (Inequality c : getInequalities()) {
				graph.addOneInequality(c);
			}
			if (USE_GRAPH) {
				for (Element e : getElements()) {
					graph.getNode(e); // create new node when necessary
				}
				// create function applications infer more relations
				for (Function f : funcs) {
					if (f.getArity() == 1) {
						for (Element e : getElements()) {
							if (! (e instanceof FunctionApplication)) {
								List<Element> lst = new ArrayList<Element>();
								lst.add(e);
								graph.getNode(new FunctionApplication(f, lst));
							}
						}
					}
					else if (f.getArity() == 2) {
						for (Element ele1 : getElements()) {
							for (Element ele2 : getElements()) {
							if (!(ele1 instanceof FunctionApplication) && !(ele2 instanceof FunctionApplication)) {							
								List<Element> lst = new ArrayList<Element>();
								lst.add(ele1);
								lst.add(ele2);
								graph.getNode(new FunctionApplication(f, lst));
							}
						}
						}
					}
					else {
						System.out.println("Warning: maynot handle functions with more than 2 parameters correctly");
					}
				}
			}
			graph.generateGraph();
			finder = new ShortestPathFinder(graph, false, true);
		}

		if (graph.hasElement(e1) && graph.hasElement(e2)) {
			if (finder.hasLeqEdge(graph.getNode(e1), graph.getNode(e2)))
				return true;
			/**
			 * The following loop is NOT used for transitivity. The reason is
			 * that constructors in Jif may be a synonym (e.g., pc_label).
			 * Looking a step before allows using assumption such as
			 * pc_label <= .. -> .. to show the relation.
			 * 
			 * A better way is to expand such labels to l_o -> l_r, so that 
			 * the following tweak is not necessary
			 */
			for (Element e : graph.getAllElements()) {
				if (finder.hasLeqEdge(graph.getNode(e1), graph.getNode(e)) && leq(e, e2, false))
					return true;
			}
			
			/**
			 * Since we are actually testing the satisfiability of the relation
			 * e1 <= e2, it is possible that the relation is satisfiable, though
			 * the relation is not derivable from the hypotheses. For instance,
			 * consider type-class constraints: list x <= Ord, with hypothesis
			 * \forall a . a <= Ord; => (list a) <= Ord and Int <= Ord;;
			 * 
			 * We cannot *derive* that list x <= Ord, since x is a variable. But
			 * it's apparently satisfiable when x = Int.
			 * 
			 * So here, we try to unify e1 and e2 with nodes in the constraint
			 * graph to test satisfiability
			 */
			if (e1.hasVars() || e2.hasVars()) {
				for (Node n1 : graph.getAllNodes()) {
					for (Node n2 : graph.getAllNodes()) {
						if (!finder.hasLeqEdge(n1, n2))
							continue;
						Map<Variable, Element> map = new HashMap<Variable, Element>();
						if (e1.matches(n1.getElement(), map)) {
							if (e2.matches(n2.getElement(), map))
								return true;
						}
					}
				}
			}
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Inequality cons : assertions)
			sb.append(cons.toString() + "; ");
		sb.append("\n");
		for (Element ele : graph.getAllElements()) {
			sb.append(ele.toString() + "; ");
		}
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Hypothesis) {
			Hypothesis other = (Hypothesis) obj;
			if (parent != null && other.parent != null)
				return parent.equals(other.parent) && assertions.equals(other.assertions);
			else if (parent == null && other.parent == null ) {
				return assertions.equals(other.assertions);
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		if (parent == null)
			return assertions.hashCode();
		else
			return parent.hashCode() * 511 + assertions.hashCode();
	}

}
