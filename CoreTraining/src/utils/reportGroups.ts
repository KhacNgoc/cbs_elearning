import { computed, ref, watch, type WatchSource } from 'vue';
import type { ChartPoint, ReportMetrics } from '../api/trainingReportApi';

/** Counts of a chart point after its name, in the backend's CHART_POINT_COLUMNS order; the average follows them. */
const CHART_POINT_FIELDS = ['total', 'complete', 'learning', 'notStarted', 'tested', 'passed', 'failed',
  'withDeadline', 'overdue', 'mandatory', 'mandatoryComplete'] as const;

/** A compact chart point as report metrics; measures the point does not carry are 0. */
export function pointMetrics(point: ChartPoint): ReportMetrics {
  const metrics: ReportMetrics = { name: String(point[0] ?? ''), total: 0, people: 0, complete: 0, learning: 0, notStarted: 0,
    tested: 0, passed: 0, failed: 0, firstPassed: 0, average: null, withDeadline: 0, overdue: 0, overduePeople: 0,
    peopleWithDeadline: 0, mandatory: 0, mandatoryComplete: 0 };
  CHART_POINT_FIELDS.forEach((field, index) => { metrics[field] = Number(point[index + 1] ?? 0); });
  const average = point[CHART_POINT_FIELDS.length + 1];
  metrics.average = average === null || average === undefined ? null : Number(average);
  return metrics;
}

export interface GroupSort<T> {
  id: string;
  /** Button text, e.g. "Nhiều lượt học nhất". */
  label: string;
  /** Completes "Hiển thị 10/57 đơn vị …", e.g. "có nhiều lượt học nhất". */
  note: string;
  /** Leaves the incoming order (biggest first) when absent. */
  compare?: (a: T, b: T) => number;
}

/** Most bars a chart draws: beyond this the bars stop being readable even when the chart grows. */
export const GROUP_CHART_LIMIT = 10;
const BAR_HEIGHT = 28, CHART_CHROME = 90, MIN_CHART_HEIGHT = 280;

/**
 * What a group chart shows out of all groups: the reader either sorts and gets the first GROUP_CHART_LIMIT, or picks
 * exact groups. Everything runs on data already loaded, so changing it never reads the report again.
 */
export function useGroupChart<T extends { name?: string }>(groups: () => T[], options: {
  noun: () => string;
  sorts: () => GroupSort<T>[];
  /** Where the full list lives, e.g. "xem đầy đủ ở bảng". */
  fullList: string;
  /** Changing this source starts over with the default order and no picks. */
  resetOn?: WatchSource;
}) {
  const sortId = ref(''), picked = ref<string[]>([]);
  const sort = computed(() => options.sorts().find(s => s.id === sortId.value) ?? options.sorts()[0]);
  const names = computed(() => groups().map(g => g.name || ''));
  const shown = computed(() => {
    const chosen = new Set(picked.value);
    const pool = chosen.size ? groups().filter(g => chosen.has(g.name || '')) : groups();
    const compare = sort.value?.compare;
    return (compare ? [...pool].sort(compare) : pool).slice(0, GROUP_CHART_LIMIT);
  });
  const height = computed(() => Math.max(MIN_CHART_HEIGHT, shown.value.length * BAR_HEIGHT + CHART_CHROME));
  const note = computed(() => {
    const count = shown.value.length, all = groups().length, noun = options.noun(), s = sort.value;
    if (picked.value.length) return `Đang so sánh ${count} ${noun} đã chọn${s ? `, xếp theo ${s.label.toLowerCase()}` : ''}.`;
    if (count < all) return `Hiển thị ${count}/${all} ${noun} ${s?.note ?? ''}; ${options.fullList}.`;
    return `Hiển thị ${all} ${noun}${s ? `, xếp theo ${s.label.toLowerCase()}` : ''}.`;
  });
  if (options.resetOn) watch(options.resetOn, () => { sortId.value = ''; picked.value = []; });
  // A new filter can drop picked groups; forget them so the count and the note stay true.
  watch(names, list => {
    if (!list.length) return;
    const known = new Set(list);
    if (picked.value.some(n => !known.has(n))) picked.value = picked.value.filter(n => known.has(n));
  });
  return { sortId: computed({ get: () => sort.value?.id ?? '', set: v => { sortId.value = v; } }), picked, names, shown, height, note };
}

/** Shortens an axis label; the tooltip still shows it whole. */
export const MAX_AXIS_LABEL = 24;
export const axisLabel = (label: string) => label.length > MAX_AXIS_LABEL ? `${label.slice(0, MAX_AXIS_LABEL - 1)}…` : label;
