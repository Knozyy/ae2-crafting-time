# Native row text when decoration is off

The requirements for [#533](https://github.com/cTux/ae2-crafting-time/issues/533)
now live in the [specification](native-row-color/spec.md), with the resolved
[technical design](native-row-color/technical-design.md) and
[implementation plan](native-row-color/implementation-plan.md).

When **Badge background** and **Fast-to-slow colors** are both off, ordinary
Crafting Plan and Crafting Status row text inherits AE2's foreground. Special
status colors retain their meaning. The specification owns the scope status
and verification requirements; this page does not claim the fix is shipped.

The original draft was merged in
[#547](https://github.com/cTux/ae2-crafting-time/pull/547). The background switch
is documented in [#532's specification](badge-background/spec.md), and
[Text shadow](text-shadow/spec.md) remains independent.
