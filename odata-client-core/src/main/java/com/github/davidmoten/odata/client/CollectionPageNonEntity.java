package com.github.davidmoten.odata.client;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public final class CollectionPageNonEntity<T> implements Iterable<T> {

    private final ContextPath contextPath;
    private final Class<T> cls;
    private final List<T> list;
    private final String nextLink;

    public CollectionPageNonEntity(ContextPath contextPath, Class<T> cls, List<T> list, String nextLink) {
        this.contextPath = contextPath;
        this.cls = cls;
        this.list = list;
        this.nextLink = nextLink;
    }

    public List<T> values() {
        return list;
    }

    public Optional<CollectionPageNonEntity<T>> nextPage() {
        if (nextLink != null) {
            // TODO
            return null;
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            CollectionPageNonEntity<T> page = CollectionPageNonEntity.this;
            int i = -1;

            @Override
            public boolean hasNext() {
                loadNext();
                return page != null;
            }

            @Override
            public T next() {
                loadNext();
                if (page == null) {
                    throw new NoSuchElementException();
                } else {
                    T v = page.list.get(i);
                    i++;
                    return v;
                }
            }

            private void loadNext() {
                if (page != null) {
                    while (true) {
                        if (page != null && i == page.list.size()) {
                            page = page.nextPage().orElse(null);
                            i = 0;
                        } else {
                            break;
                        }
                    }
                }
            }

        };
    }

}
