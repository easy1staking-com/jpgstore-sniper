use sniper/utils.{calculate_fee, calculate_locked_amount}

test calculate_fee_basic() {
  // 100 ADA at 2%, min 1 ADA → max(2_000_000, 1_000_000) = 2_000_000
  calculate_fee(100_000_000, 200, 1_000_000) == 2_000_000
}

test calculate_fee_1_percent() {
  // 100 ADA at 1%, min 1 ADA → max(1_000_000, 1_000_000) = 1_000_000
  calculate_fee(100_000_000, 100, 1_000_000) == 1_000_000
}

test calculate_fee_min_kicks_in() {
  // 5 ADA at 1%, min 1 ADA → max(50_000, 1_000_000) = 1_000_000
  calculate_fee(5_000_000, 100, 1_000_000) == 1_000_000
}

test calculate_fee_zero_pct_with_min() {
  // 100 ADA at 0%, min 1 ADA → max(0, 1_000_000) = 1_000_000
  calculate_fee(100_000_000, 0, 1_000_000) == 1_000_000
}

test calculate_fee_zero_pct_zero_min() {
  // 100 ADA at 0%, min 0 → max(0, 0) = 0
  calculate_fee(100_000_000, 0, 0) == 0
}

test calculate_fee_zero_amount() {
  // 0 ADA at 2%, min 1 ADA → max(0, 1_000_000) = 1_000_000
  calculate_fee(0, 200, 1_000_000) == 1_000_000
}

test calculate_locked_amount_standard() {
  // max_price=100 ADA, op_fee=2 ADA, proto_fee=1 ADA, budget=3 ADA
  // locked = 100 + 2 + 1 + 3 = 106 ADA
  calculate_locked_amount(100_000_000, 2_000_000, 1_000_000, 3_000_000) == 106_000_000
}

test calculate_locked_amount_zero_proto_fee() {
  // max_price=100 ADA, op_fee=2 ADA, proto_fee=0, budget=3 ADA
  // locked = 100 + 2 + 0 + 3 = 105 ADA
  calculate_locked_amount(100_000_000, 2_000_000, 0, 3_000_000) == 105_000_000
}

test calculate_locked_amount_zero_proto_fee_2() {
  calculate_locked_amount(100_000_000, 2_000_000, 0, 3_000_000) == 105_000_000
}
