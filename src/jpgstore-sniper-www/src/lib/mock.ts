import { Settings, SnipeOrder } from "./types";
import { calculateFees } from "./fees";

export const MOCK_SETTINGS: Settings = {
  operatorFeePct: 2,
  protocolFeePct: 1,
  minOperatorFee: 1_000_000, // 1 ADA
  minProtocolFee: 500_000, // 0.5 ADA
  txFeeBudget: 500_000, // 0.5 ADA
  protocolTreasury:
    "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjc7",
};

function mockOrder(
  id: string,
  policyId: string,
  collectionName: string,
  maxPriceAda: number,
  createdAt: string
): SnipeOrder {
  const maxPrice = maxPriceAda * 1_000_000;
  const fees = calculateFees(MOCK_SETTINGS, maxPrice);
  return {
    id,
    policyId,
    collectionName,
    maxPrice,
    operatorFee: fees.operatorFee,
    protocolFee: fees.protocolFee,
    txFeeBudget: fees.txFeeBudget,
    totalLocked: fees.totalLocked,
    status: "active",
    createdAt,
  };
}

export const MOCK_SNIPES: SnipeOrder[] = [
  mockOrder(
    "snipe-001",
    "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8",
    "SpaceBudz",
    45,
    "2026-02-18T10:30:00Z"
  ),
  mockOrder(
    "snipe-002",
    "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc",
    "Clay Nation",
    25,
    "2026-02-19T14:15:00Z"
  ),
  mockOrder(
    "snipe-003",
    "40fa2aa67258b4ce7b5782f74831d46a84c59a0ff0c28262fab21728",
    "Jpg.store Founder",
    120,
    "2026-02-19T16:45:00Z"
  ),
  mockOrder(
    "snipe-004",
    "1ec85dcee27f2d90ec1f9a1e4ce74a667dc9be8b184463223f9c9601",
    "Cornucopias",
    8,
    "2026-02-20T08:00:00Z"
  ),
];
