package constraint.ast;

import graph.ConstraintGraph;
import graph.pathfinder.PathFinder;
import graph.pathfinder.ShortestPathFinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Environment represents hypothesis (a conjunction of inequalities)
 */
public class Environment {
	private Set<Inequality> assertions;
	private ConstraintGraph graph;
	private PathFinder finder = null;
	private boolean SHOW_HYPOTHESIS = false;
	private Environment parent = null; // used to reduce shared environments
										// (e.g., to store global assumptions)

	/**
	 * Empty assumption
	 */
	public Environment() {
		assertions = new HashSet<Inequality>();
		graph = new ConstraintGraph(null, new HashSet<Constraint>(), false);
	}

	/**
	 * @param s
	 *            A set of inequalities
	 */
	public Environment(Set<Inequality> s) {
		assertions = s;
		graph = new ConstraintGraph(null, new HashSet<Constraint>(), false);
	}

	/**
	 * Add an inequality to the assumption
	 * 
	 * @param ieq
	 *            An inequality to be added
	 */
	public void addInequality(Inequality ieq) {
		assertions.add(ieq.baseInequality());
	}

	/**
	 * Add all inequalities in <code>e</code> to the assumption
	 * 
	 * @param e
	 *            An assumption to be added
	 */
	public void addEnv(Environment e) {
		if (parent == null) {
			parent = e;
		} else {
			assertions.addAll(e.getInequalities());
		}
	}

	/**
	 * Return a fresh assumption where an inequality <code>e1</code> <=
	 * <code>e2</code> is added to the current assumption
	 * 
	 * @param e1
	 *            Element on LHS
	 * @param e2
	 *            Element on RHS
	 * @return A fresh assumption where an inequality <code>e1</code> <=
	 *         <code>e2</code> is added to the current assumption
	 */
	public Environment addLeq(Element e1, Element e2) {
		Environment e = new Environment();
		e.addEnv(this);
		e.addInequality(new Inequality(e1.getBaseElement(),
				e2.getBaseElement(), Relation.LEQ));
		return e;
	}

	/**
	 * @return A string of all inequalities
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
	 * from the assumption
	 * 
	 * @param e1
	 *            Element on LHS
	 * @param e2
	 *            Element on RHS
	 * @return True if <code>e1</code> <= <code>e2</code>
	 */
	public boolean leq(Element p1, Element p2) {
		Element e1 = p1.getBaseElement();
		Element e2 = p2.getBaseElement();

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
		if (leqApplyAssertions(e1, e2))
			return true;

		// in principle, all partial orderings can be inferred on the hypothesis
		// graph. But since the graph is constructed from constraints, a
		// relation can be missing if the element to be tested is not present in
		// the graph (e.g., A <= A join B). One way is to incrementally add the
		// extra nodes to a saturated constraint graph. Here, we apply the
		// direct rules for constructors, joins and meets for better performance
		
		// the following test is require only when e1 or e2 is not represented
		// in hypothesis graph
		if (graph.hasElement(e1) && graph.hasElement(e2)) {
			return false;
		}

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
			boolean contravariant = ((ConstructorApplication) e1).getCons()
					.isContraVariant();
			for (int i = 0; i < l1.size(); i++) {
				if (!contravariant && !leq(l1.get(i), l2.get(i)))
					return false;
				else if (contravariant && !leq(l2.get(i), l1.get(i)))
					return false;
			}
			return true;
		}

		// apply the inference rules for joins and meets
		if (e1 instanceof JoinElement) {
			for (Element e : ((JoinElement) e1).getElements())
				if (!leq(e, e2))
					return false;
			return true;
		} else if (e1 instanceof MeetElement) {
			for (Element e : ((MeetElement) e1).getElements())
				if (leq(e, e2))
					return true;
			return false;
		}

		if (e2 instanceof JoinElement) {
			for (Element e : ((JoinElement) e2).getElements())
				if (leq(e1, e))
					return true;
			return false;
		} else if (e2 instanceof MeetElement) {
			for (Element e : ((MeetElement) e2).getElements())
				if (!leq(e1, e))
					return false;
			return true;
		}

		return false;
	}

	/**
	 * @return All inequalities made in the assumption
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
	 * Saturate a constraint graph from all assumptions to test if
	 * <code>e1</code><=<code>e2</code> can be inferred
	 * 
	 * @param e1
	 *            Element on LHS
	 * @param e2
	 *            Element on RHS
	 * @return True if <code>e1</code><=<code>e2</code> can be inferred from
	 *         assumption
	 */
	private boolean leqApplyAssertions(Element e1, Element e2) {

		if (finder == null) {
			for (Inequality c : getInequalities()) {
				graph.addOneInequality(c);
			}
			graph.generateGraph();

			if (SHOW_HYPOTHESIS) {
				graph.labelAll();
			}
			finder = new ShortestPathFinder(graph);
		}

		if (graph.hasElement(e1) && graph.hasElement(e2)) {
			if (finder.getPath(graph.getNode(e1), graph.getNode(e2), false) != null)
				return true;
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
		if (obj instanceof Environment) {
			Environment other = (Environment) obj;
			for (Inequality cons : assertions) {
				if (!other.assertions.contains(cons))
					return false;
			}

			for (Inequality cons : other.assertions) {
				if (!assertions.contains(cons))
					return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int ret = 0;
		for (Inequality cons : assertions)
			ret += cons.hashCode();
		return ret;
	}

}
