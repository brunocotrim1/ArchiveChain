#include "por.hpp"

#include <filesystem>


int bucket_id(merkle::HashT<32> h, int num_buckets) {
    return h % num_buckets;
}


int main() {
    por::PoRepT<32, merkle::sha256, 2, 64>  p;
    int buckets = 1000;
    uint64_t dis[buckets];

    for (int i = 0; i < buckets; i++) {
        dis[i] = 0;
    }

    for (auto itr = p.search.begin(); itr != p.search.end(); itr++) {
        dis[bucket_id(*itr, buckets)] += 1;
    }

    std::ofstream outfile ("dist.csv");

    for (int i = 0; i < buckets; i++) {
        outfile << i << "," << dis[i] << std::endl;
    }

    outfile.close();
}