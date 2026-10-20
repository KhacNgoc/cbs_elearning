import type {
  OrganizationUnit,
  UnitBranch,
  UnitTreeRow,
} from "../types/organization";

const collator = new Intl.Collator("vi", {
  numeric: true,
  sensitivity: "base",
});

/** Missing parents are scope boundaries: keep the accessible subtree visible. */
export function buildUnitForest(
  units: readonly OrganizationUnit[],
): UnitBranch[] {
  const nodes = new Map<string, UnitBranch>();
  for (const unit of units) nodes.set(unit.id, { unit, children: [] });
  const roots: UnitBranch[] = [];
  for (const node of nodes.values()) {
    const parent = node.unit.parentId
      ? nodes.get(node.unit.parentId)
      : undefined;
    let current = parent;
    const seen = new Set([node.unit.id]);
    let cycle = false;
    while (current) {
      if (seen.has(current.unit.id)) {
        cycle = true;
        break;
      }
      seen.add(current.unit.id);
      current = current.unit.parentId
        ? nodes.get(current.unit.parentId)
        : undefined;
    }
    if (parent && !cycle) parent.children.push(node);
    else roots.push(node);
  }
  const lists = [roots];
  while (lists.length) {
    const list = lists.pop()!;
    list.sort(
      (a, b) =>
        collator.compare(a.unit.name, b.unit.name) ||
        collator.compare(a.unit.id, b.unit.id),
    );
    for (const node of list)
      if (node.children.length) lists.push(node.children);
  }
  return roots;
}

/** Filter nodes while retaining ancestors so results never lose their context. */
export function filterUnitForest(
  forest: readonly UnitBranch[],
  matches: (unit: OrganizationUnit) => boolean,
): UnitBranch[] {
  const result: UnitBranch[] = [];
  for (const branch of forest) {
    const children = filterUnitForest(branch.children, matches);
    if (matches(branch.unit) || children.length)
      result.push({ unit: branch.unit, children });
  }
  return result;
}

export function flattenUnitForest(
  forest: readonly UnitBranch[],
  expanded?: ReadonlySet<string>,
): UnitTreeRow[] {
  const result: UnitTreeRow[] = [];
  const pending = forest
    .map((branch, index) => ({
      branch,
      depth: 0,
      ancestors: [] as string[],
      position: String(index + 1),
    }))
    .reverse();
  while (pending.length) {
    const item = pending.pop()!;
    result.push({
      unit: item.branch.unit,
      depth: item.depth,
      ancestors: item.ancestors,
      position: item.position,
      hasChildren: item.branch.children.length > 0,
    });
    if (!expanded || expanded.has(item.branch.unit.id)) {
      for (let index = item.branch.children.length - 1; index >= 0; index--) {
        pending.push({
          branch: item.branch.children[index]!,
          depth: item.depth + 1,
          ancestors: [...item.ancestors, item.branch.unit.id],
          position: `${item.position}.${index + 1}`,
        });
      }
    }
  }
  return result;
}

/** Exclude an edited unit and its entire subtree from valid parent choices. */
export function unitDescendants(
  units: readonly OrganizationUnit[],
  id: string,
): Set<string> {
  const result = new Set<string>();
  if (!id) return result;
  const children = new Map<string, string[]>();
  for (const unit of units) {
    if (unit.parentId)
      children.set(unit.parentId, [
        ...(children.get(unit.parentId) || []),
        unit.id,
      ]);
  }
  const pending = [id];
  while (pending.length) {
    const current = pending.pop()!;
    if (result.has(current)) continue;
    result.add(current);
    pending.push(...(children.get(current) || []));
  }
  return result;
}
