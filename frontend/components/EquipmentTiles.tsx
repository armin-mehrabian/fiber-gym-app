// The equipment picker shared by onboarding and settings: one tile per item, tap to toggle.
import Tile from "@/components/Tile";
import type { Equipment } from "@/lib/types";

type EquipmentTilesProps = {
  equipment: Equipment[] | null; // null = the list couldn't be loaded
  selected: string[]; // equipment ids
  onToggle: (id: string) => void;
  testIdPrefix: string; // "equipment-" in onboarding, "settings-equipment-" in settings
  disabled?: boolean;
};

export default function EquipmentTiles({ equipment, selected, onToggle, testIdPrefix, disabled }: EquipmentTilesProps) {
  // Without the list there is nothing to pick from; the saved choice is kept as it is.
  if (!equipment) {
    return <p className="text-sm tracking-[0.12em] text-mut">Equipment list unavailable</p>;
  }
  return (
    <div className="grid grid-cols-2 gap-2">
      {equipment.map((item) => (
        <Tile
          key={item.id}
          selected={selected.includes(item.id)}
          onClick={() => onToggle(item.id)}
          testId={`${testIdPrefix}${item.id}`}
          disabled={disabled}
        >
          {item.name}
        </Tile>
      ))}
    </div>
  );
}
