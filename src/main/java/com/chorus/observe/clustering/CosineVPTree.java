package com.chorus.observe.clustering;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vantage-Point Tree (VP-Tree) for cosine-similarity range queries.
 * <p>
 * All input vectors are assumed to be L2-normalized (unit length).
 * Cosine similarity between two unit vectors equals their dot product.
 * Cosine distance is defined as {@code 1 - dotProduct} (range {@code [0, 2]}).
 * <p>
 * Complexity:
 * <ul>
 *   <li>Build: O(N log N) average</li>
 *   <li>Range query: O(log N) average per query (prunes distant subtrees)</li>
 * </ul>
 * <p>
 * For small datasets ({@code N < 1000}), a linear scan is often faster due to
 * tree-building overhead. Callers should choose the appropriate strategy.
 */
final class CosineVPTree {

    private final Node root;
    private final List<float[]> points;

    CosineVPTree(@NonNull List<float[]> points) {
        this.points = List.copyOf(points);
        if (this.points.isEmpty()) {
            this.root = null;
        } else {
            int[] indices = new int[this.points.size()];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            this.root = build(indices, 0, indices.length);
        }
    }

    /**
     * Find all point indices whose cosine similarity to the query vector
     * is >= {@code minSimilarity}.
     *
     * @param query          query vector (must be unit-length)
     * @param minSimilarity  minimum cosine similarity threshold ({@code [-1, 1]})
     * @return list of matching point indices (includes the query point itself if present)
     */
    @NonNull List<Integer> rangeQuery(@NonNull float[] query, double minSimilarity) {
        List<Integer> result = new ArrayList<>();
        if (root == null) return result;
        double maxDistance = 1.0 - minSimilarity; // convert similarity threshold to distance threshold
        rangeQuery(root, query, maxDistance, result);
        return result;
    }

    private void rangeQuery(Node node, float[] query, double maxDistance, List<Integer> result) {
        double dist = cosineDistance(points.get(node.vantagePoint), query);
        if (dist <= maxDistance) {
            result.add(node.vantagePoint);
        }

        // Triangle inequality pruning:
        // |dist(q, vp) - dist(p, vp)| <= dist(q, p)
        // If dist(q, vp) - mu > maxDist, then ALL points in left subtree are farther than maxDist
        // If mu - dist(q, vp) > maxDist, then ALL points in right subtree are farther than maxDist
        if (node.left != null && dist - maxDistance <= node.mu) {
            rangeQuery(node.left, query, maxDistance, result);
        }
        if (node.right != null && dist + maxDistance >= node.mu) {
            rangeQuery(node.right, query, maxDistance, result);
        }
    }

    private Node build(int[] indices, int from, int to) {
        if (from >= to) return null;

        // Select vantage point: use first element (deterministic given stable input)
        int vpIdx = indices[from];
        Node node = new Node(vpIdx);

        if (to - from == 1) {
            return node;
        }

        // Compute distances from vantage point to all other points in this subset
        double[] distances = new double[to - from - 1];
        for (int i = from + 1; i < to; i++) {
            distances[i - from - 1] = cosineDistance(points.get(vpIdx), points.get(indices[i]));
        }

        // Partition around median distance using quickselect-style partitioning
        double median = quickSelectMedian(distances);
        node.mu = median;

        // Reorder indices: closer points first, farther points second
        int leftEnd = from + 1;
        for (int i = from + 1; i < to; i++) {
            double d = cosineDistance(points.get(vpIdx), points.get(indices[i]));
            if (d <= median) {
                // Swap to left partition
                int tmp = indices[leftEnd];
                indices[leftEnd] = indices[i];
                indices[i] = tmp;
                leftEnd++;
            }
        }

        node.left = build(indices, from + 1, leftEnd);
        node.right = build(indices, leftEnd, to);
        return node;
    }

    /**
     * Compute cosine distance between two unit vectors: {@code 1 - dot(a, b)}.
     */
    private static double cosineDistance(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return 1.0 - dot;
    }

    /**
     * Find median of a double array using quickselect (O(n) average).
     */
    private static double quickSelectMedian(double[] arr) {
        if (arr.length == 0) return 0.0;
        double[] copy = arr.clone();
        int n = copy.length;
        int mid = n / 2;
        double median = quickSelect(copy, 0, n - 1, mid);
        // For even-length arrays, average of two middle elements
        if (n % 2 == 0) {
            double lower = quickSelect(copy.clone(), 0, n - 1, mid - 1);
            median = (lower + median) / 2.0;
        }
        return median;
    }

    private static double quickSelect(double[] arr, int left, int right, int k) {
        while (true) {
            if (left == right) return arr[left];
            int pivotIndex = partition(arr, left, right);
            if (k == pivotIndex) {
                return arr[k];
            } else if (k < pivotIndex) {
                right = pivotIndex - 1;
            } else {
                left = pivotIndex + 1;
            }
        }
    }

    private static int partition(double[] arr, int left, int right) {
        double pivot = arr[right];
        int storeIndex = left;
        for (int i = left; i < right; i++) {
            if (arr[i] < pivot) {
                double tmp = arr[storeIndex];
                arr[storeIndex] = arr[i];
                arr[i] = tmp;
                storeIndex++;
            }
        }
        double tmp = arr[storeIndex];
        arr[storeIndex] = arr[right];
        arr[right] = tmp;
        return storeIndex;
    }

    private static final class Node {
        final int vantagePoint;
        double mu; // median distance
        Node left;
        Node right;

        Node(int vantagePoint) {
            this.vantagePoint = vantagePoint;
        }
    }
}
