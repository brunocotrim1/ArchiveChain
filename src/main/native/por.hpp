#include "merkle.hpp"
#include "sloth256_189.h"
#include <filesystem>
#include <fstream>
#include <set>
#include <math.h>
#include <algorithm>
#include <map>
#include <unistd.h>

namespace fs = std::filesystem;

namespace por
{
    int logb(int n, int b)
    {
        return log(n) / log(b);
    }

    template <typename Set>
    auto closest_element(Set &set, const typename Set::value_type &value) -> decltype(set.begin())
    {
        const auto it = set.lower_bound(value);
        if (it == set.begin())
            return it;

        const auto prev_it = std::prev(it);
        return (it == set.end() || value - *prev_it <= *it - value) ? prev_it : it;
    }

    template <
        size_t HASH_SIZE,
        size_t FANOUT,
        size_t LEAVES>
    struct ProofT
    {

        /// @brief The type of hashes in the tree
        typedef merkle::HashT<HASH_SIZE> Hash;

        size_t n = logb(LEAVES, FANOUT) * (FANOUT - 1) + 2;
        std::vector<Hash> hashes;

        ProofT() {}

        ProofT(const std::string &s)
        {
            for (size_t i = 0; i < n; i++)
            {
                Hash h(s.substr(i * HASH_SIZE * 2, HASH_SIZE * 2));
                hashes.push_back(h);
            }
        }

        ProofT(const uint8_t *bytes)
        {
            size_t i = 0;
            while (i < n)
            {
                Hash h(bytes + i * sizeof(uint8_t));
                hashes.push_back(h);
                i++;
            }
        }

        ProofT(const std::vector<uint8_t> &bytes, const std::vector<int> &indexes, bool flag = false)
        {
            size_t position;
            size_t begin = 0;
            uint64_t offset = merkle::deserialise_uint64_t(bytes, begin);
            size_t i = 0;
            if (flag)
            {
                n = indexes.size();
            }
            while (i < n)
            {
                position = indexes.at(i) * HASH_SIZE + begin;
                Hash h(bytes, position);
                hashes.push_back(h);
                i++;
            }
        }

        Hash root()
        {
            return hashes[n - 1];
        }

        Hash at(int i)
        {
            if (i > hashes.size())
                throw std::runtime_error("Index out of range.");
            return hashes[i];
        }

        double quality(Hash challenge)
        {
            std::set<Hash> nodes;

            for (size_t i = 0; i < hashes.size() - 1; i++)
            {
                nodes.insert(hashes[i]);
            }

            auto node = *closest_element(nodes, challenge);

            Hash h;

            for (size_t i = 0; i < HASH_SIZE / 2; i++)
            {
                h.bytes[i] = root().bytes[i];
            }

            for (size_t i = HASH_SIZE / 2; i < HASH_SIZE; i++)
            {
                h.bytes[i] = node.bytes[i];
            }

            Hash dif = h < challenge ? challenge - h : h - challenge;
            std::cout << "dif: " << dif.to_uint64() << std::endl;
            return (double)dif.to_uint64() / (double)(challenge.to_uint64() + h.to_uint64());
        }

        std::string to_string() const
        {
            std::string s;

            for (size_t i = 0; i < hashes.size(); i++)
            {
                std::string h = hashes[i].to_string();
                s = s + h;
                std::cout << "hash string size: " << h.length() << std::endl;
            }

            return s;
        }
    };

    /// @brief Template for Proof-of-Replication
    /// @tparam HASH_SIZE Size of each hash in number of bytes
    /// @tparam HASH_FUNCTION The hash function
    /// @tparam FANOUT The fanout value of the Merkle Tree
    /// @tparam LEAVES The number of leaves of the Merkle Tree
    template <
        size_t HASH_SIZE,
        void HASH_FUNCTION(
            const std::vector<merkle::HashT<HASH_SIZE>> &values,
            size_t start,
            size_t len,
            merkle::HashT<HASH_SIZE> &out),
        size_t FANOUT,
        size_t LEAVES>
    class PoRepT
    {
    public:
        /// @brief The type of hashes in the tree
        typedef merkle::HashT<HASH_SIZE> Hash;

        /// @brief The type of the tree
        typedef merkle::TreeT<HASH_SIZE, HASH_FUNCTION, FANOUT, LEAVES> Tree;

        /// @brief The type of the proof
        typedef ProofT<HASH_SIZE, FANOUT, LEAVES> Proof;

        PoRepT() {}

        void load_plot(std::string path)
        {
            // std::string path = "./plot";
            for (const auto &entry : fs::directory_iterator(path))
            {
                Hash h(entry.path().filename());
                search.insert(h);
            }
        }

        // void encode(std::vector<Hash>& v) {
        //     std::vector<Hash> values;
        //     Hash res;
        //     Hash root = v.back();

        //     for (int i = 0; i < v.size() - 1; i++) {
        //         std::vector parents = get_parent_indexes(i);
        //         for (int j = 0; j < parents.size(); j++) {
        //             values.push_back(v[parents[j]]);
        //         }

        //         Hash hi;
        //         hi.bytes[HASH_SIZE-1] = i;
        //         values.push_back(hi);

        //         HASH_FUNCTION(values, 0, parents.size() + 1, res);
        //         for(uint8_t j = 0; j < HASH_SIZE; j++) {
        //             v[i].bytes[j] = v[i].bytes[j] ^ res.bytes[j];
        //         }
        //         values.clear();
        //     }

        //     // for (int i = 0; i < v.size() - 1; i++) {
        //     //     Hash hi;
        //     //     hi.bytes[HASH_SIZE-1] = i;
        //     //     values.push_back(v.back());
        //     //     values.push_back(hi);
        //     //     HASH_FUNCTION(values, 0, 2, res);
        //     //     for(uint8_t j = 0; j < HASH_SIZE; j++) {
        //     //         v[i].bytes[j] = v[i].bytes[j] ^ res.bytes[j];
        //     //     }
        //     //     values.clear();
        //     // }
        // }

        void vde(Hash &inout)
        {
            sleep(0.5000521559995832);
        }

        void vdd(Hash &inout)
        {
            sleep(0.03474049910109898);
        }

        void encode(std::vector<Hash> &v)
        {
            std::map<int, int> dep;
            for (int i = 0; i < LEAVES; i += FANOUT)
            {
                std::vector<int> indexes = get_path_indexes(i);
                int i0 = indexes[0];
                int i1 = indexes[1];
                dep[i0] = indexes[2];
                dep[i1] = indexes[2];

                for (int j = 2; j < indexes.size() - 1; j++)
                {
                    dep[indexes[j]] = indexes[j + 1];
                }
            }

            vde(v.back());

            for (int i = v.size() - 2; i >= 0; i--)
            {
                int parentIndex = dep[i];
                Hash parent = v[parentIndex];
                for (uint8_t j = 0; j < HASH_SIZE; j++)
                {
                    v[i].bytes[j] = v[i].bytes[j] ^ parent.bytes[j];
                }

                vde(v[i]);
            }
        }

        Proof decode(Proof p)
        {
            Proof decoded;

            for (int i = 0; i < p.n - 1; i++)
            {
                Hash parent = (i == 0 || i == 1) ? p.at(2) : p.at(i + 1);
                Hash dec;
                for (uint8_t j = 0; j < HASH_SIZE; j++)
                {
                    dec.bytes[j] = p.at(i).bytes[j] ^ parent.bytes[j];
                }

                vdd(dec);
                decoded.hashes.push_back(dec);
            }
            vdd(p.hashes[p.n - 1]);
            decoded.hashes.push_back(p.root());
            return decoded;
        }

        Proof decode(Proof p, std::vector<int> indexes)
        {
            std::vector<Hash> values;
            Hash res;
            Proof decoded;
            Hash t;
            for (int i = 0; i < indexes.size() - 1; i++)
            {
                Hash hi;
                hi.bytes[HASH_SIZE - 1] = indexes.at(i);
                values.push_back(p.root());
                values.push_back(hi);
                HASH_FUNCTION(values, 0, 2, res);
                for (uint8_t j = 0; j < HASH_SIZE; j++)
                {
                    t.bytes[j] = p.at(i).bytes[j] ^ res.bytes[j];
                }
                decoded.hashes.push_back(t);
                values.clear();
            }
            return decoded;
        }

        void plot(char *filename, const char *plotFolder)
        {
            createDirectory(plotFolder);
            std::ifstream f(filename, std::ifstream::binary);
            if (!f.good())
                throw std::runtime_error("Cannot plot from invalid file");

            std::vector<uint8_t> bytes;
            char t;
            int offset = 0;

            while (!f.eof())
            {
                int n = LEAVES * HASH_SIZE;
                while (!f.eof() && n-- > 0)
                {
                    f.read(&t, 1);
                    bytes.push_back(t);
                }
                if (n > 0)
                {
                    while (n-- > 0)
                    {
                        bytes.push_back('\n');
                    }
                }
                std::cout << "n: " << bytes.size() << std::endl;

                if (n < 0)
                {
                    Tree tree(bytes, offset);
                    encode(tree.nodes);
                    // std::cout << "tree nodes: " << tree.nodes.<< std::endl;
                    if (search.insert(tree.root()).second == false)
                    {
                        conflicts++;
                    }
                    else
                    {
                        tree.serialize(plotFolder + tree.root().to_string());
                        plots++;
                    }
                }
                bytes.clear();
                offset++;
            }
            // std::cout << "conflicts: " << conflicts << std::endl;
            // std::cout << "plots: " << plots << std::endl;
            f.close();
            for (const auto &hash : search)
            {
                std::cout << hash.to_string() << std::endl;
            }
        }

        std::vector<int> get_path_indexes(int index)
        {
            std::vector<int> indexes;

            int n = index / FANOUT;

            for (int i = 0; i < FANOUT; i++)
            {
                indexes.push_back(n * FANOUT + i);
            }

            int base = LEAVES;
            int level = 1;
            while (pow(FANOUT, level) < LEAVES)
            {
                int node = base + (int)index / (pow(FANOUT, level));

                int b = node / FANOUT;

                for (int i = 0; i < FANOUT; i++)
                {
                    if (FANOUT * b + i != node)
                        indexes.push_back(FANOUT * b + i);
                }
                base = base + (int)(LEAVES) / pow(FANOUT, level++);
            }
            indexes.push_back(base);
            return indexes;
        }

        /// @brief Generates a Proof from the plot given a challenge
        /// @param challenge
        Proof generate_proof(Hash challenge, const char *plotFolder)
        {
            createDirectory(plotFolder);
            std::vector<int> indexes = get_path_indexes(challenge % LEAVES);
            auto closest = *closest_element(search, challenge);

            std::ifstream f(plotFolder + closest.to_string(), std::ifstream::binary);
            if (!f.good())
                throw std::runtime_error("Invalid plot file");

            std::vector<uint8_t> bytes;
            char t;
            while (!f.eof())
            {
                f.read(&t, 1);
                bytes.push_back(t);
            }
            f.close();

            Proof proof(bytes, indexes, false);
            return proof;
        }

        std::vector<Hash> decode(std::vector<Hash> &v)
        {
            std::map<int, int> dep;
            for (int i = 0; i < LEAVES; i += FANOUT)
            {
                std::vector<int> indexes = get_path_indexes(i);
                int i0 = indexes[0];
                int i1 = indexes[1];
                dep[i0] = indexes[2];
                dep[i1] = indexes[2];

                for (int j = 2; j < indexes.size() - 1; j++)
                {
                    dep[indexes[j]] = indexes[j + 1];
                }
            }

            std::vector<Hash> decoded(v.size());

            for (int i = v.size() - 2; i >= 0; i--)
            {
                int parentIndex = dep[i];
                Hash parent = v[parentIndex];
                for (uint8_t j = 0; j < HASH_SIZE; j++)
                {
                    decoded[i].bytes[j] = v[i].bytes[j] ^ parent.bytes[j];
                }
                // vdd(decoded[i]);
            }

            return decoded;
        }

        std::vector<std::vector<uint8_t>> retrieveOriginal(std::string filePath)
        {
            std::cout << "decoding filePath: " << filePath << std::endl;
            std::ifstream f(filePath, std::ifstream::binary);
            if (!f.good())
                throw std::runtime_error("Invalid plot file");

            std::vector<uint8_t> bytes;
            char t;
            while (!f.eof())
            {
                f.read(&t, 1);
                bytes.push_back(t);
            }
            constexpr int SIZE = 127; // Size of the vector
            std::vector<int> indexes;

            // Fill the vector with values from 0 to 126
            for (int i = 0; i < SIZE; ++i)
            {
                indexes.push_back(i);
            }
            f.close();
            Proof proof(bytes, indexes, true);

            std::vector<Hash> decoded = decode(proof.hashes);
            std::vector<Hash> nodes = decoded;

            std::vector<std::vector<uint8_t>> byteArrays; // Vector to hold byte arrays
            nodes = decoded;
            for (int i = 0; i < LEAVES; i++)
            {
                std::vector<uint8_t> byteArray(nodes[i].bytes, nodes[i].bytes + 32); // Copy bytes to vector
                std::string str(reinterpret_cast<const char *>(nodes[i].bytes), reinterpret_cast<const char *>(nodes[i].bytes) + 32);
                // std::cout << "decoded: " << str << std::endl;
                byteArrays.push_back(byteArray); // Add byte array to vector
            }
            return byteArrays;
        }

        /// @brief Computes the Merkle root from a path
        /// @param p Proof that contains the hashes from a Merkle path
        /// @param indexes Set of indexes that indicate the order of the path in the proof
        Hash compute_root(Proof *p, const std::vector<int> &indexes)
        {
            Hash res;
            std::vector<Hash> values;
            for (int i = 0; i < FANOUT; i++)
            {
                values.push_back(p->hashes[i]);
            }

            int node;
            int base = LEAVES;
            int level = 1;
            int index;
            for (int i = 0; i < logb(LEAVES, FANOUT); i++)
            {
                HASH_FUNCTION(values, FANOUT * i, FANOUT, res);
                node = base + (int)indexes.at(0) / (pow(FANOUT, level));
                int n = 0;
                index = values.size() - i;

                while (indexes.at(index) < node)
                {
                    values.push_back(p->hashes[index++]);
                    n++;
                }
                values.push_back(res);
                while (FANOUT - 1 - n > 0)
                {
                    values.push_back(p->hashes[index++]);
                    n++;
                }

                base = base + LEAVES / pow(FANOUT, level++);
            }

            return res;
        }

        bool verify(Proof p, Hash challenge)
        {
            std::vector<int> indexes = get_path_indexes(challenge % LEAVES);
            Proof d = decode(p);
            Hash root = compute_root(&d, indexes);

            return root == p.root();
        }

        bool verify(std::string proof, std::string challenge)
        {
            auto c = Hash(challenge);
            auto p = Proof(proof);
            bool v = verify(p, c);

            return v;
        }

        Hash challenge()
        {
            return Hash();
        }

        int get_plots()
        {
            return plots;
        }

        int get_conflicts()
        {
            return conflicts;
        }

        // protected:
        std::set<Hash> search;
        int conflicts = 0;
        int plots = 0;
        bool createDirectory(const std::string &path)
        {
            if (!fs::exists(path))
            {
                try
                {
                    fs::create_directory(path);
                    return true;
                }
                catch (const std::exception &e)
                {
                    std::cerr << "Error creating directory: " << e.what() << std::endl;
                    return false;
                }
            }
            return true; // Directory already exists
        }
    };

    typedef PoRepT<32, merkle::sha256, 2, 64> PoRep;

}