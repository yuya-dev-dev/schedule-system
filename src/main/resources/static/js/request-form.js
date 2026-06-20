document.addEventListener("DOMContentLoaded", () => {
    const workType = document.getElementById("workType");
    const requester = document.getElementById("requesterName");
    const requiredMarker = document.getElementById("requester-required");

    if (!workType || !requester || !requiredMarker) {
        return;
    }

    const updateRequesterRequirement = () => {
        const requesterIsOptional = workType.value === "RECEIVING"
            || workType.value === "PRODUCT_MANAGEMENT";
        requester.required = !requesterIsOptional;
        requiredMarker.hidden = requesterIsOptional;
    };

    workType.addEventListener("change", updateRequesterRequirement);
    updateRequesterRequirement();
});
