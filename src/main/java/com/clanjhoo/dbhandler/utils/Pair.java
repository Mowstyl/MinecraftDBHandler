package com.clanjhoo.dbhandler.utils;

/**
 * A tuple containing two items of any kind
 * @param <F> the type of the first item in the tuple
 * @param <S> the type of the second item in the tuple
 */
public class Pair<F, S> {
    private F first;
    private S second;

    /**
     * Creates a new instance of a Pair with the given values stored in it
     * @param first the first element in the tuple
     * @param second the second element in the tuple
     */
    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element in the tuple
     * @return the first element
     */
    public F getFirst() {
        return first;
    }

    /**
     * Returns the second element in the tuple
     * @return the second element
     */
    public S getSecond() {
        return second;
    }

    /**
     * Sets the first element of the tuple to the specified value
     * @param first the element to be stored in the first position of the tuple
     */
    public void setFirst(F first) {
        this.first = first;
    }

    /**
     * Sets the second element of the tuple to the specified value
     * @param second the element to be stored in the second position of the tuple
     */
    public void setSecond(S second) {
        this.second = second;
    }
}
