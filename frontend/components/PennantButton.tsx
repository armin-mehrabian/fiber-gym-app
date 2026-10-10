// The gold primary action. The notched right edge makes it look like a varsity pennant.
type PennantButtonProps = {
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
  testId: string;
  children: React.ReactNode;
};

export default function PennantButton({ onClick, type = "button", disabled, testId, children }: PennantButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      data-testid={testId}
      className="flex min-h-[60px] w-full shrink-0 items-center justify-center bg-acc pr-[18px] font-display text-xl text-on-acc [clip-path:polygon(0_0,100%_0,calc(100%-18px)_50%,100%_100%,0_100%)] disabled:opacity-40"
    >
      {children}
    </button>
  );
}
