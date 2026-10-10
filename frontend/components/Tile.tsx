// A toggle button used for picking options (split, equipment, units).
// Selected = gold border on the highlight background, like a chosen jersey on the rack.
type TileProps = {
  selected: boolean;
  onClick: () => void;
  testId: string;
  disabled?: boolean;
  // Replaces the default size (height, padding, text size). It replaces rather than adds,
  // because two Tailwind classes for the same property don't override each other by order.
  sizeClassName?: string;
  children: React.ReactNode;
};

const DEFAULT_SIZE = "min-h-16 px-2.5 text-[0.95rem]";

export default function Tile({ selected, onClick, testId, disabled, sizeClassName = DEFAULT_SIZE, children }: TileProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      // aria-pressed tells screen readers this is an on/off choice.
      aria-pressed={selected}
      data-testid={testId}
      className={`border-[1.5px] font-bold ${sizeClassName} ${
        selected ? "border-acc bg-hl text-fg" : "border-edge text-mut"
      }`}
    >
      {children}
    </button>
  );
}
