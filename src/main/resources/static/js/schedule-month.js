document.addEventListener("DOMContentLoaded", () => {
    const scheduleScroll = document.querySelector(".schedule-scroll");
    const focusDate = scheduleScroll?.dataset.initialFocusDate;
    if (!scheduleScroll || !focusDate) return;
    if (scheduleScroll.scrollWidth <= scheduleScroll.clientWidth) return;

    const targetHeading = scheduleScroll.querySelector(`[data-work-date="${focusDate}"]`);
    const timeHeading = scheduleScroll.querySelector(".time-heading");
    if (!targetHeading || !timeHeading) return;

    scheduleScroll.scrollLeft = Math.max(0, targetHeading.offsetLeft - timeHeading.offsetWidth);
});
