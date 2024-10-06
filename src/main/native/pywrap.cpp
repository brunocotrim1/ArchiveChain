#include <pybind11/pybind11.h>
#include "por.cpp"
using namespace por;
namespace py = pybind11;
constexpr auto byref = py::return_value_policy::reference_internal;

PYBIND11_MODULE(por_binding, m) {
    m.doc() = "pybind11 example plugin"; // optional module docstring

    m.def("verify", &verify, "A function that verifies a proof given a challenge");
}