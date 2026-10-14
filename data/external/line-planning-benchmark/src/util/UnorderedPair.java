package util;

import java.util.Objects;

public class UnorderedPair<A, B>
{
    private final A first;
    private final B second;

    public UnorderedPair(A first, B second)
    {
        this.first = first;
        this.second = second;
    }

    public A getFirst()
    {
        return first;
    }

    public B getSecond()
    {
        return second;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof UnorderedPair<?, ?> other)) return false;

        // Direct match: (A, B) == (A, B)
        boolean direct = Objects.equals(first, other.first)
                      && Objects.equals(second, other.second);

        // Swapped match: (A, B) == (B, A)
        boolean swapped = Objects.equals(first, other.second)
                       && Objects.equals(second, other.first);

        return direct || swapped;
    }

    @Override
    public int hashCode()
    {
        // Order-independent hash
        int h1 = Objects.hashCode(first);
        int h2 = Objects.hashCode(second);

        // Commutative combination so hash(a,b) == hash(b,a)
        return h1 + h2;
    }
}
