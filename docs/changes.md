# Problem Formulation Changes

## 1. Line set

The original formulation defines:

$$
\mathcal L
=
\{\ell\mid \ell\text{ is a feasible simple path in }G\}.
$$

In the revised formulation, $\mathcal L$ is a finite, pre-generated subset of the operationally feasible simple paths in $G$:

$$
\mathcal L
\subseteq
\{\ell\mid
\ell\text{ is an operationally feasible simple path in }G
\}.
$$

The MILP selects the operating frequency of each candidate line in $\mathcal L$.

## 2. Passenger-trip set

For each OD pair $(o,d)$, $\mathcal P_{od}$ is a finite set of feasible passenger trips generated from the candidate lines in $\mathcal L$:

$$
\mathcal P_{od}
=
\left\{
p\mid
p\text{ is a retained feasible passenger trip from }o\text{ to }d
\text{ using lines in }\mathcal L
\right\}.
$$

Passenger itineraries whose route cost is at most 15 minutes greater than that of the shortest realizable itinerary are retained. This screening is performed before the itineraries are expanded over the allowed frequency combinations and before frequency-dependent waiting time is added to $c_p$.

## 3. Unserved demand and objective function

Introduce the nonnegative variable:

$$
u_{od}^{t}\geq0,
$$

representing demand from $o$ to $d$ in period $t$ that is not assigned to a trip in $\mathcal P_{od}$.

The objective function becomes:

$$
\min
\sum_{t\in\mathcal T}
\sum_{(o,d)}
\sum_{p\in\mathcal P_{od}}
c_p x_{od,p}^{t}
+
M
\sum_{t\in\mathcal T}
\sum_{(o,d)}
u_{od}^{t}.
$$

Here, $M>0$ is a sufficiently large penalty for unserved demand.

## 4. Demand constraint

The demand constraint becomes:

$$
\sum_{p\in\mathcal P_{od}}
x_{od,p}^{t}
+u_{od}^{t}
=
D_{od}^{t}
\qquad
\forall(o,d),t.
$$

If $\mathcal P_{od}=\varnothing$, this constraint reduces to:

$$
u_{od}^{t}=D_{od}^{t}.
$$
